package com.rrdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rrdp.dto.Result;
import com.rrdp.dto.UserDTO;
import com.rrdp.entity.Blog;
import com.rrdp.entity.User;
import com.rrdp.mapper.BlogMapper;
import com.rrdp.mapper.UserMapper;
import com.rrdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rrdp.service.IUserService;
import com.rrdp.utils.SystemConstants;
import com.rrdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lebrwcd
 * @since 2022/11/6
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
}
