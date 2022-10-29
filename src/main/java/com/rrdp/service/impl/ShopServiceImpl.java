package com.rrdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.rrdp.dto.Result;
import com.rrdp.entity.Shop;
import com.rrdp.mapper.ShopMapper;
import com.rrdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.rrdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.rrdp.utils.RedisConstants.CACHE_SHOP_TTL;

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
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis中查询商铺缓存
        String shopCache = stringRedisTemplate.opsForValue().get(key);
        // 2. 缓存是否命中
        if (StrUtil.isNotEmpty(shopCache)) {
            // 2.1 命中，直接返回
            Shop shop = JSONUtil.toBean(shopCache, Shop.class);
            return Result.ok(shop);
        }
        // 2.2 未命中，根据id从数据库查询
        Shop shop = query().eq("id",id).one();
        // 3. 判断商铺是否存在
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        // 4. 写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }
}
