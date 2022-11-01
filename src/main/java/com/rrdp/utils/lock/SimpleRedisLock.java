package com.rrdp.utils.lock;/**
 * @author lebrwcd
 * @date 2022/11/1
 * @note
 */

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * ClassName SimpleRedisLock
 * Description Redis实现分布式锁
 *
 * @author lebr7wcd
 * @version 1.0
 * @date 2022/11/1
 */
public class SimpleRedisLock implements ILock{

    // 锁名称
    private String name;

    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate,String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 当前线程标识
        long threadId = Thread.currentThread().getId();
        // 获取锁  互斥  同时设置超时时间
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        // 防止npe
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
