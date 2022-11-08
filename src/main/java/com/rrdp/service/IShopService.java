package com.rrdp.service;

import com.rrdp.dto.Result;
import com.rrdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author lebrwcd
 * @date 2022/10/29
 */
public interface IShopService extends IService<Shop> {

    /**
     * 修改商铺 缓存更新方案
     * @param shop 商铺
     * @return 无
     */
    Result update(Shop shop);

    /**
     * 根据id查询商铺信息
     * @param id id
     * @return 无
     */
    Result queryShopCache(Long id);

    /**
     * 店铺类型查询店铺信息
     * @param typeId 店铺类型
     * @param current 当前页
     * @param x 经度
     * @param y 纬度
     * @return 无
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
