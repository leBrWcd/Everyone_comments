package com.rrdp.utils.lock;/**
 * @author lebrwcd
 * @date 2022/11/1
 * @note
 */

import cn.hutool.core.lang.UUID;
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

    /**
     * 锁前缀
     */
    private static final String KEY_PREFIX = "lock:";

    /**
     * UUID 区分不同 JVM
     */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate,String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 当前线程标识 不同UUID + 不同的线程ID 保证线程标识唯一
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁  互斥  同时设置超时时间
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId , timeoutSec, TimeUnit.SECONDS);
        // 防止npe
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 获取当前线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        String lockId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        // 判断当前线程标识是否与
        if (threadId.equals(lockId)) {
            // 一致，删除
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
        // 不一致不管
    }
}
