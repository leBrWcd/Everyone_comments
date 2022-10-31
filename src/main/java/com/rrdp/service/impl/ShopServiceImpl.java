package com.rrdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.rrdp.dto.Result;
import com.rrdp.entity.Shop;
import com.rrdp.mapper.ShopMapper;
import com.rrdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rrdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.Time;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.rrdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lebrwcd
 * @since 2022/10/29
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    // 线程池 - 用于缓存重建开启独立线程
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    @Transactional
    public Result update(Shop shop) {
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        // 3.返回
        return Result.ok();
    }

    @Override
    public Result queryShopCache(Long id) {
        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 缓存击穿 - 互斥锁
        // Shop shop = queryWithMutex(id);

        // 缓存穿透 - 逻辑过期
        Shop shop = queryWithLogic(id);
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 缓存击穿 - 逻辑过期方案
     * @param id id
     * @return 商铺信息
     */
    public Shop queryWithLogic(Long id) {

        String key = CACHE_SHOP_KEY + id;
        // 1.从redis中查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否命中
        if (StrUtil.isBlank(shopCache)) {
            // 2.1 未命中，直接返回null
            return null;
        }
        // 3.命中，判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(shopCache, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 3.1 未过期，返回商铺数据
            return shop;
        }
        // 3.2 已过期，缓存重建
        // 4.缓存重建
        // 4.1 获取互斥锁，判断获取互斥锁是否成功
        String lockKey = LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)) {
            // 4.2 成功，开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit( () -> {
                try {
                    this.saveShop2Redis(id,30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 4.3 获取锁失败，返回商铺信息
        // 5.获取锁成功与否都要返回
        return shop;
    }

    /** 商铺查询 - 解决缓存击穿  互斥锁方法
     * @param id 商铺主键
     * @return 商铺信息
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis中查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否命中
        if (StrUtil.isNotBlank(shopCache)) {
            // 2.1 命中，返回数据
            return JSONUtil.toBean(shopCache,Shop.class);
        }
        // 判断命中的是否是空值 : ""
        if (shopCache != null) {
            // 不为null就是 ""
            return null;
        }
        // 3.未命中，尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            if (!tryLock(lockKey)) {
                // 3.1 获取锁失败，休眠
                Thread.sleep(50);
                // 3.2 重试
                return queryWithMutex(id);
            }
            // 3.3 获取锁,根据id从数据库查询
            shop = getById(id);
            // 模拟重建的延时
            Thread.sleep(200);
            if (shop == null) {
                // 数据库也不存在，防止缓存穿透，将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 3.4 写入缓存
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 4.释放互斥锁
            unlock(key);
        }
        // 5.返回
        return shop;
    } // end of queryWithMutex

    /**
     * 商铺查询 - 解决缓存穿透
     * @param id 商铺主键
     * @return 商铺信息
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis中查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        // 2. 缓存是否命中
        /**
         * StrUtil.isNotBlank(null)  false
         * StrUtil.isNotBlank("") false  空串返回false
         * StrUtil.isNotBlank("\t\n") false
         * StrUtil.isNotBlank("abc") true  有值
         */
        if (StrUtil.isNotBlank(shopCache)) {
            // 2.1 命中，直接返回
            return JSONUtil.toBean(shopCache, Shop.class);
        }
        // 判断命中的是否是空值
        if (shopCache != null) {
            // 不为null就是 ""
            return null;
        }
        // 2.2 未命中，根据id从数据库查询
        Shop shop = query().eq("id", id).one();
        // 3. 判断商铺是否存在
        if (shop == null) {
            // 将空值写入有效期，解决缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 4. 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return  shop;
    } // end of queryWithPassThrough

    /**
     * 添加商铺信息到redis
     * @param id 商铺主键
     * @param expireMinutes 逻辑过期时间
     */
    public void saveShop2Redis(Long id,Long expireMinutes) {
        // 1.查询数据库
        Shop shop = getById(id);
        // 2.封装
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(expireMinutes));
        // 3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 尝试获得锁
     * @param key key
     * @return true：获得 false ：没有获得
     */
    private  boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
