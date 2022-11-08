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


    @GetMapping("/of/follow")
    public Result scrollPage(@RequestParam("lastId") Long lastId,
                             @RequestParam(value = "offset",defaultValue = "0") Integer offset) {
        return blogService.scrollPage(lastId,offset);
    }

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 根据id查询博主的探店笔记
     * @param current 当前页
     * @param id 用户id
     * @return 无
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 查询笔记点赞排行榜
     * @param id
     * @return
     */
    @GetMapping("/likes/{id}")
    public Result queryBloglikes(@PathVariable("id") Long id) {
        return blogService.queryBloglikes(id);
    }

    /**
     * 点赞笔记
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 关于我
     * @param current
     * @return
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    /**
     * 热门笔记
     * @param current
     * @return
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }
}
