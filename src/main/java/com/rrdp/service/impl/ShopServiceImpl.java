package com.rrdp.service.impl;

import com.rrdp.dto.Result;
import com.rrdp.entity.Shop;
import com.rrdp.mapper.ShopMapper;
import com.rrdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rrdp.utils.redis.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.rrdp.utils.redis.RedisConstants.*;

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
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    // 线程池 - 用于缓存重建开启独立线程
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 先更新数据库，再删除缓存，采用事务控制
     * @param shop 商铺
     * @return 无
     */
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
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id , Shop.class, this::getById, CACHE_SHOP_TTL , TimeUnit.MINUTES);

        // 缓存击穿 - 互斥锁
        //Shop shop1 = cacheClient
        //        .queryWithMutex(CACHE_SHOP_KEY,id,Shop.class, this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        // 缓存穿透 - 逻辑过期
        //Shop shop2 = cacheClient
        //        .queryWithLogic(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }
}
