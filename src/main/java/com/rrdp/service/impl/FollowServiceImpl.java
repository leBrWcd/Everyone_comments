package com.rrdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rrdp.dto.Result;
import com.rrdp.dto.UserDTO;
import com.rrdp.entity.Follow;
import com.rrdp.mapper.FollowMapper;
import com.rrdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rrdp.service.IUserService;
import com.rrdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.rrdp.utils.SystemConstants.FOLLOWS_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lebrwcd
 * @since 2022/11/6
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result followOrNot(Long followUserid) {
        // 1.获取当前的用户Id
        UserDTO user = UserHolder.getUser();
        // 2.判断当前用户是否有关注
        Integer count = query().eq("user_id", user.getId()).eq("follow_user_id", followUserid).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followUser(Long followUserid, boolean isFollow) {
        UserDTO user = UserHolder.getUser();
        // 1.判断是取消关注还是关注
        if (isFollow) {
            // 关注(之前页面状态时未关注)
            Follow follow = new Follow();
            follow.setFollowUserId(followUserid);
            follow.setUserId(user.getId());
            boolean save = save(follow);
            if (save) {
                // 把关注用户存入redis
                stringRedisTemplate.opsForSet().add(FOLLOWS_KEY + user.getId(),followUserid.toString());
            }
        } else { //(之前页面状态时已关注)
            // 3.取关，删除 delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean remove = remove(new QueryWrapper<Follow>()
                    .eq("user_id", user.getId()).eq("follow_user_id", followUserid));
            if (remove) {
                //从redis中移除
                stringRedisTemplate.opsForSet().remove(FOLLOWS_KEY + user.getId(),followUserid.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result common(Long id) {
        // 1.获取当前用户id
        Long userId = UserHolder.getUser().getId();
        // 2.求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(FOLLOWS_KEY + userId, FOLLOWS_KEY + id);
        // 3.判断交集
        if (intersect == null || intersect.isEmpty()) {
            // 3.1 为空返回空
            return Result.ok(Collections.emptyList());
        }
        // 3.2 不为空，解析拿出用户列表
        List<Long> ids = intersect
                .stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        List<UserDTO> userDTOList = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
