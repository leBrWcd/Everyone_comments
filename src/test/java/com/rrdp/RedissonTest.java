package com.rrdp;/**
 * @author lebrwcd
 * @date 2022/11/2
 * @note
 */

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
*ClassName RedissonTest
*Description TODO
*@author lebr7wcd
*@date 2022/11/2
*@version 1.0
*/
@SpringBootTest
public class RedissonTest {

    @Resource
    private RedissonClient redissonClient;

    @Test
    void testRedisson() {

        RLock lock = redissonClient.getLock("anyLock");
        try {
            boolean islock = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (islock) {
                System.out.println("业务代码");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

    }

}
