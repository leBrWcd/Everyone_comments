package com.rrdp.utils.redis;

/**
 * @description: Redis常量类
 * @author : lebrwcd
 * @date : 2022/10/28
 *
 */
public class RedisConstants {
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

    /**
     * 缓存空值有效时间
     */
    public static final Long CACHE_NULL_TTL = 2L;

    /**
     * 商户缓存有效时间
     */
    public static final Long CACHE_SHOP_TTL = 30L;
    /**
     * 商户缓存 key
     */
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
