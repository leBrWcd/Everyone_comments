package com.rrdp.utils.redis;/**
 * @author lebrwcd
 * @date 2022/10/31
 * @note
 */

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.rrdp.utils.redis.RedisConstants.*;

/**
 * ClassName CacheClient
 * Description Redis缓存工具类
 *
 * @author lebr7wcd
 * @version 1.0
 * @date 2022/10/31
 */
@Slf4j
@Component
public class CacheClient {

    @Resource
    private  StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    /**
     * 方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    /**
     * 方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿
     * @param key key
     * @param value 对象值
     * @param time 时间
     * @param unit 单位
     */
    public void setWithLogicalExpire(String key,Object value,Long time,TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(unit.toMinutes(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     * @param keyPrefix 缓存key前缀
     * @param id id
     * @param type 反序列化的对象类型
     * @param dbFallback <ID,R> 等于  R r = getById(id) 有参有返回值的函数
     * @return 反序列化对象
     */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                         Function<ID,R> dbFallback, Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 缓存是否命中
        if (StrUtil.isNotBlank(json)) {
            // 2.1 命中，直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值
        if (json != null) {
            // 不为null就是 ""
            return null;
        }
        // 2.2 未命中，根据id从数据库查询
        R r = dbFallback.apply(id);
        // 3. 判断商铺是否存在
        if (r == null) {
            // 将空值写入有效期，解决缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 4. 写入redis
       this.set(key,r,time,unit);
        return  r;
    } // end of queryWithPassThrough


    /**
     * 方法4：根据指定的key查询缓存，并反序列化为指定类型，利用互斥锁的方式解决缓存击穿问题
     * @param keyPrefix key前缀
     * @param id id
     * @param type 反序列化对象类型
     * @param dbFallback 函数式调用
     * @param time 时间
     * @param unit 单位
     * @param <R> 函数式调用返回值
     * @param <ID> 函数式调用参数
     * @return R
     */
    public <R,ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type,
                                   Function<ID,R> dbFallback, Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否命中
        if (StrUtil.isNotBlank(json)) {
            // 2.1 命中，返回数据
            return JSONUtil.toBean(json,type);
        }
        // 判断命中的是否是空值 : ""
        if (json != null) {
            // 不为null就是 ""
            return null;
        }
        // 3.未命中，尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            if (!tryLock(lockKey)) {
                // 3.1 获取锁失败，休眠
                Thread.sleep(50);
                // 3.2 重试
                return queryWithMutex(keyPrefix,id,type,dbFallback,time,unit);
            }
            // 3.3 获取锁,根据id从数据库查询
            r = dbFallback.apply(id);
            if (r == null) {
                // 数据库也不存在，防止缓存穿透，将空值写入redis
                this.set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            // 3.4 写入缓存
            this.set(key,JSONUtil.toJsonStr(r),time,unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 4.释放互斥锁
            unlock(lockKey);
        }
        // 5.返回
        return r;
    } // end of queryWithMutex


    /**
     * 方法5：根据指定的key查询缓存，并反序列化为指定类型，利用逻辑过期的方式解决缓存击穿问题
     * @param keyPrefix key 前缀
     * @param id id
     * @param type 反序列化对象类型
     * @param dbFallback 函数式调用
     * @param time 时间
     * @param unit 单位
     * @param <R> 函数式调用返回值
     * @param <ID> 函数式调用参数
     * @return R
     */
    public <R,ID> R queryWithLogic(String keyPrefix, ID id, Class<R> type,
                                   Function<ID,R> dbFallback, Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否命中
        if (StrUtil.isBlank(json)) {
            // 2.1 未命中，直接返回null
            return null;
        }
        // 3.命中，判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 3.1 未过期，返回商铺数据
            return r;
        }
        // 3.2 已过期，缓存重建
        // 4.缓存重建
        // 4.1 获取互斥锁，判断获取互斥锁是否成功
        String lockKey = LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)) {
            // 4.2 成功，开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit( () -> {
                try {
                    R rFromDB = dbFallback.apply(id);
                    this.setWithLogicalExpire(key,rFromDB,time,unit);
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
        return r;
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
