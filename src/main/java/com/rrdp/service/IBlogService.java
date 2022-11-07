package com.rrdp.service;

import com.rrdp.dto.Result;
import com.rrdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author lebrwcd
 * @since 2022/11/6
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 根据id查询博客信息
     * @param id 博客id
     * @return 无
     */
    Result queryById(Long id);

    /**
     * 根据当前页查询热门博客
     * @param current 当前页码
     * @return 无
     */
    Result queryHotBlog(Integer current);

    /**
     * 查询我发布的博客
     * @param current 当前页
     * @return 无
     */
    Result queryMyBlog(Integer current);

    /**
     * 点赞笔记
     * @param id 笔记id
     * @return 无
     */
    Result likeBlog(Long id);

    /**
     * 查询笔记点赞排行榜
     * @param id 笔记id
     * @return 无
     */
    Result queryBloglikes(Long id);

    /**
     * 保存笔记
     * @param blog 笔记
     * @return 无
     */
    Result saveBlog(Blog blog);

    /**
     * 实现滚动分页
     * @param lastId 最小时间戳
     * @param offset 偏移量
     * @return 无
     */
    Result scrollPage(Long lastId, Integer offset);
}
