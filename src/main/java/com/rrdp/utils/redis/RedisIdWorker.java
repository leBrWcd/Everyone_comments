package com.rrdp.utils.redis;/**
 * @author lebrwcd
 * @date 2022/10/31
 * @note
 */

import cn.hutool.core.io.resource.StringResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * ClassName RedisIdWorker
 * Description 全局唯一ID生成器  正号0 + （时间戳）31位 + （自增长）32位
 *
 * @author lebr7wcd
 * @version 1.0
 * @date 2022/10/31
 */
@Component
public class RedisIdWorker {

    /**
     * 开始的时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /**
     * 左移位数
     */
    private static final int LEFT_MOVE_BIT = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 全局唯一ID生成
     * @param keyPrefix key前缀
     * @return 全局唯一ID
     */
    public long nextId(String keyPrefix) {

        // 1.时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowTime = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowTime - BEGIN_TIMESTAMP;

        // 2.自增序列号
        // 2.1 获取当前日期，精确到天，作为key的一部分，提高利用率，同时能够便于后续统计业务
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long serial = stringRedisTemplate.opsForValue().increment("incr:" + keyPrefix + ":" + date);

        // 3. 拼接时间戳和序列号  时间戳高位，序列号地位位  先将时间戳左移32位，那么最右32位都是0，接着或上序列号
        return timeStamp << LEFT_MOVE_BIT | serial;
    }

}
