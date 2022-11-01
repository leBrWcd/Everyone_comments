package com.rrdp;

import com.rrdp.service.impl.ShopServiceImpl;
import com.rrdp.utils.redis.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    private final ExecutorService executorService = Executors.newFixedThreadPool(300);;

    @Test
    void testWorker() throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            // 一个任务执行300次
            for (int i = 0; i < 300; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id= " + id);
            }
            // 内部维护变量 -1
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        // 一个线程执行300次， 300 * 300 = 90000次
        for (int i = 0; i < 300; i++) {
            executorService.submit(task);
        }
        // 阻塞，直到countDown = 0
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }


}
