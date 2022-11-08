package com.rrdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.rrdp.dto.Result;
import com.rrdp.entity.Shop;
import com.rrdp.mapper.ShopMapper;
import com.rrdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rrdp.utils.SystemConstants;
import com.rrdp.utils.redis.CacheClient;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {

        // 1.判断是否需要地理坐标查询
        if (current == null) {
            //不需要，走数据库查询
            List<Shop> shops = query().eq("type_id", typeId).list();
            return Result.ok(shops);
        }
        // 2.计算分页参数
        Integer from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        Integer end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 3.查询redis，按照距离排序，分页。 结果包含：shopId,distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        // 4.解析出商铺id
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        List<Long> ids = new ArrayList<>(content.size());
        Map<String,Distance> distanceMap = new HashMap<>(content.size());
        if (from >= content.size()) {
            return Result.ok(Collections.emptyList());
        }
        // skip：跳过前几条
        content.stream().skip(from).forEach( e -> {
            // 商铺id
            String strId = e.getContent().getName();
            ids.add(Long.valueOf(strId));
            // 距离distance
            Distance distance = e.getDistance();
            // 封装商铺id -> distance
            distanceMap.put(strId,distance);
        });
        // 5.根据商铺id查询商铺信息
        String idStr = StrUtil.join(",", ids);
        List<Shop> shopList = query().in("id", ids).last("ORDER BY FIELD (id," + idStr + ")").list();
        shopList.forEach( shop -> {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        });
        // 最终要的数据是商铺，商铺里包含了distance
        return Result.ok(shopList);
    }
}
