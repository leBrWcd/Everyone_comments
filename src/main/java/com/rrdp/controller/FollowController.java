package com.rrdp.controller;


import com.rrdp.dto.Result;
import com.rrdp.dto.ScrollResult;
import com.rrdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author lebrwcd
 * @since 2022/11/6
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;


    /**
     * 共同关注
     * @param id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result common(@PathVariable("id") Long id) {
        return followService.common(id);
    }

    /**
     * 是否有关注某用户
     * @param followUserid 某用户id
     * @return 无
     */
    @GetMapping("/or/not/{id}")
    public Result followOrNot(@PathVariable("id") Long followUserid) {
        return followService.followOrNot(followUserid);
    }

    /**
     * 关注某用户
     * @param followUserid 某用户id
     * @param isFollow 是否有关注 有则取消关注 没有则关注
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result followUser(@PathVariable("id") Long followUserid,@PathVariable("isFollow") boolean isFollow) {
        return followService.followUser(followUserid,isFollow);
    }


}