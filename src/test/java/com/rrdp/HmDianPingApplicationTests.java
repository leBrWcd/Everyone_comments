package com.rrdp;

import com.rrdp.entity.Shop;
import com.rrdp.service.impl.ShopServiceImpl;
import com.rrdp.utils.redis.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    private final ExecutorService executorService = Executors.newFixedThreadPool(300);;

    @Test
    void testLoadData() {

        // 1.查询商铺数据
        List<Shop> list = shopService.list();
        // 2.根据typeId进行分组，typeId一致的同一组 map<typeId,List<Shop>>
        Map<Long,List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批写入Redis
        for (Map.Entry<Long,List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> geoLocations = new ArrayList<>(value.size());
            value.forEach( shop -> {
                geoLocations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),new Point(shop.getX(),shop.getY())));
            });
            // 写入Redis
            stringRedisTemplate.opsForGeo().add(key,geoLocations);
            /*
            // 每个shop都遍历执行一次redis命令，效率不好
            value.forEach( shop -> {
                stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
            });*/
        }
    }


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
