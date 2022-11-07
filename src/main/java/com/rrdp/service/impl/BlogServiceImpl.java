package com.rrdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rrdp.dto.Result;
import com.rrdp.dto.ScrollResult;
import com.rrdp.dto.UserDTO;
import com.rrdp.entity.Blog;
import com.rrdp.entity.Follow;
import com.rrdp.entity.User;
import com.rrdp.mapper.BlogMapper;
import com.rrdp.mapper.UserMapper;
import com.rrdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rrdp.service.IFollowService;
import com.rrdp.service.IUserService;
import com.rrdp.utils.SystemConstants;
import com.rrdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.rrdp.utils.SystemConstants.FEED_BOX_KEY;
import static com.rrdp.utils.SystemConstants.FOLLOWS_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lebrwcd
 * @since 2022/11/6
 */
@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result queryById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记未发布!");
        }
        fillUser(blog);
        // 查询笔记是否被当前用户点赞
        this.isLikeBlog(blog);
        return Result.ok(blog);
    }

    /**
     * 判断当前用户是否点赞笔记
     * @param blog
     * @return
     */
    private void isLikeBlog(Blog blog) {
        // 1.获取当前用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        // 2.判断点赞用户是否存在
/*        Boolean isMember = stringRedisTemplate.opsForSet()
                .isMember();*/
        Double score = stringRedisTemplate.opsForZSet().score(SystemConstants.BLOG_LIKE_KEY + blog.getId(), user.getId().toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        // 查询笔记是否被当前用户点赞
        records.forEach( record -> {
            this.isLikeBlog(record);
            this.fillUser(record);
        });
        return Result.ok(records);
    }

    /**
     * 封装用户信息
     * @param blog 博客
     */
    private void fillUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1.获取当前用户
        UserDTO user = UserHolder.getUser();
        // 2.判断当前用户是否点赞过
        Double score = stringRedisTemplate.opsForZSet().score(SystemConstants.BLOG_LIKE_KEY + id, user.getId().toString());
        // 3.未点赞，可以点
        if (score == null) {
            // 3.1 数据库点赞数 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2 redis保存用户
            if (isSuccess) {
                // 采用sortedSet 进行点赞排行
                stringRedisTemplate.opsForZSet().add(SystemConstants.BLOG_LIKE_KEY + id,user.getId().toString(),System.currentTimeMillis());
            }
        } else {
            // 4.已点赞再点了一次（取消点赞）
            // 4.1 数据库点赞数 - 1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2 redis移除点赞用户
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(SystemConstants.BLOG_LIKE_KEY + id,user.getId().toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBloglikes(Long id) {

        // 查询笔记点赞前5名 zrange key 0 4
        Set<String> range = stringRedisTemplate.opsForZSet().range(SystemConstants.BLOG_LIKE_KEY + id, 0, 4);
        if (range == null || range.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 解析,收集Long类型的id
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());
        // 根据用户id查询用户 WHERE id IN ( 1010 , 2 ) order by FIELD(id,1010,2)
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOList = userService.query().in("id",ids).last("ORDER BY FIELD(id,"+ idStr +")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        // 返回list
        return Result.ok(userDTOList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2.保存探店博文
        boolean save = save(blog);
        if (save) {
            // 3.查询发布该笔记作者的所有粉丝  select * from tb_follow where follow_user_id = ?
            List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
            // 4.推送笔记id给所有粉丝
            follows.forEach( follow -> {
                // 获取粉丝id
                Long userId = follow.getUserId();
                // 采用sortedSet实现排名同时实现滚动分页
                stringRedisTemplate.opsForZSet().add(FEED_BOX_KEY + userId,blog.getId().toString(),System.currentTimeMillis());
            });
        }
        // 返回id
        return Result.ok(blog.getId());
    }


    /**
     * 滚动分页
     * @param lastId 上一次最小时间戳
     * @param offset 偏移量
     * @return 滚动分页返回对象
     */
    @Override
    public Result scrollPage(Long lastId, Integer offset) {

        // 1.获取当前用户
        UserDTO user = UserHolder.getUser();
        // 2.查询收件箱  ZREVRANGEBYSCORE key Max Min LIMIT offset count
        Set<ZSetOperations.TypedTuple<String>> scoreWithScores = stringRedisTemplate.opsForZSet()
                // 参数一：key，参数二：min 参数三：max 参数四：offset 参数五：count
                .reverseRangeByScoreWithScores(FEED_BOX_KEY + user.getId(),0,lastId,offset,2);
        if (scoreWithScores == null || scoreWithScores.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 3.解析收件箱 blogId minTine offset
        List<Long> ids = new ArrayList<>();
        long minTime = 0;
        Integer os = 1;
        for (ZSetOperations.TypedTuple<String> scoreWithScore : scoreWithScores) {
            ids.add(Long.valueOf(scoreWithScore.getValue()));
            long time = scoreWithScore.getScore().longValue();
            if (minTime == time) {
                os++;
            } else {
                // 不等，那么后面的时间戳肯定是更小的，同时需要重置偏移
                minTime = time;
                os = 1;
            }
        }
        String idStr = StrUtil.join(",", ids);
        // 4.根据id查询笔记列表
        List<Blog> blogList = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        blogList.forEach( blog -> {
            this.isLikeBlog(blog);
            this.fillUser(blog);
        });
        // 5.封装返回结果
        ScrollResult sr = new ScrollResult();
        sr.setList(blogList);
        sr.setMinTime(minTime);
        sr.setOffset(os);
        return Result.ok(sr);
    }
}
