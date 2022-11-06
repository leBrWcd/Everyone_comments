package com.rrdp.service;

import com.rrdp.dto.Result;
import com.rrdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author lebrwcd
 * @since 2022/11/6
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 判断是否有关注该用户
     * @param followUserid 当前笔记用户Id
     * @return 无
     */
    Result followOrNot(Long followUserid);

    Result followUser(Long followUserid, boolean isFollow);

    Result common(Long id);
}
