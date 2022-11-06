package com.rrdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rrdp.dto.Result;
import com.rrdp.dto.UserDTO;
import com.rrdp.entity.Blog;
import com.rrdp.entity.User;
import com.rrdp.service.IBlogService;
import com.rrdp.service.IUserService;
import com.rrdp.utils.SystemConstants;
import com.rrdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author lebrwcd
 * @since 2022/11/6
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @GetMapping("{id}")
    public Result queryBlog(@PathVariable("id") Long id) {
        return blogService.queryById(id);
    }

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        blogService.save(blog);
        // 返回id
        return Result.ok(blog.getId());
    }


    @GetMapping("/likes/{id}")
    public Result queryBloglikes(@PathVariable("id") Long id) {
        return blogService.queryBloglikes(id);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }
}
