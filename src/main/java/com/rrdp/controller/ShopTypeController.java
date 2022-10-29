package com.rrdp.controller;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.rrdp.dto.Result;
import com.rrdp.entity.ShopType;
import com.rrdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author Lebrwcd
 * @since 2022/10/29
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        // 1.从redis中查询
        String key = "cache:shop_type";
        //  String shopType = stringRedisTemplate.opsForList().leftPop(key);
        String shopType = stringRedisTemplate.opsForValue().get(key);
        // 2.是否命中缓存
        // 2.1 命中，直接返回
        if (StrUtil.isNotEmpty(shopType)) {
            List<ShopType> shopTypes = JSONUtil.toList(shopType, ShopType.class);
            return Result.ok(shopTypes);
        }
        // 2.2 无命中，数据库查询
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        //   stringRedisTemplate.opsForList().leftPush(key,JSONUtil.toJsonStr(typeList));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(typeList));
        // 存入redis，返回
        return Result.ok(typeList);
    }
}
