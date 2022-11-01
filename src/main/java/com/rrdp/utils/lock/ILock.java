package com.rrdp.utils.lock;

/**
 * @author lebrwcd
 * @date 2022/11/1
 * @note 分布式锁 Lock接口
 */
public interface ILock {

    /**
     * 尝试获得锁
     * @param timeoutSec 锁持有的超时时间，过期自动释放锁
     * @return true 获取锁成功 false 获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();

}
