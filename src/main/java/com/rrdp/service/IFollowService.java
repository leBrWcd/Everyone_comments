package com.rrdp.service;

import com.rrdp.dto.Result;
import com.rrdp.dto.ScrollResult;
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

    /**
     * 关注用户
     * @param followUserid
     * @param isFollow
     * @return
     */
    Result followUser(Long followUserid, boolean isFollow);

    /**
     * 共同关注
     * @param id
     * @return
     */
    Result common(Long id);

}
