package com.rrdp.utils;

/**
 * @description: 常量工具类
 * @author : lebrwcd
 * @date : 2022/10/28
 *
 */
public class SystemConstants {
    /**
     * 图片上传路径
     */
    public static final String IMAGE_UPLOAD_DIR = "D:\\redisExp\\nginx-1.18.0\\nginx-1.18.0\\html\\hmdp\\imgs\\";
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
     * 存入redis的验证码 key
     */
    public static final String LOGIN_CODE_PRE = "login:code:";
    /**
     * 存入redis key 的有效时间（分钟）
     */
    public static final Long LOGIN_CODE_TTL = 2L;
    /**
     * 存入redis user的token前缀
     */
    public static final String LOGIN_TOKEN_PRE = "login:token:";
    /**
     * 存入redis user的token 有效时间（分钟）
     */
    public static final Long LOGIN_TOKEN_TTL = 30L;
}
