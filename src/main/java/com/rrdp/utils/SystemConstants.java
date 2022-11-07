package com.rrdp.utils;

/**
 * @description: 系统常量工具类
 * @author : lebrwcd
 * @date : 2022/10/28
 *
 */
public class SystemConstants {
    /**
     * 图片上传路径
     */
    public static final String IMAGE_UPLOAD_DIR = "D:\\redisExpStatic\\nginx-1.18.0\\nginx-1.18.0\\html\\rrdp\\imgs\\";
    /**
     * 用户随机昵称前缀
     */
    public static final String USER_NICK_NAME_PREFIX = "user_";
    /**
     * 默认分页页码
     */
    public static final int DEFAULT_PAGE_SIZE = 5;
    /**
     * 最大页码
     */
    public static final int MAX_PAGE_SIZE = 10;
    /**
     * 笔记点赞列表 KEY
     */
    public static final String BLOG_LIKE_KEY = "blog:like:";
    /**
     * 好友关注列表 KEY
     */
    public static final String FOLLOWS_KEY = "follows:";

    /**
     * 收件箱key
     */
    public static final String FEED_BOX_KEY = "feed:box:";
}
