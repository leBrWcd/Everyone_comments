package com.rrdp.config;/**
 * @author lebrwcd
 * @date 2022/11/2
 * @note
 */

import io.lettuce.core.RedisClient;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ClassName RedissonConfig
 * Description Redisson配置
 *
 * @author lebr7wcd
 * @version 1.0
 * @date 2022/11/2
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.110.135:6379")
                .setPassword("wcd0209");
        return Redisson.create(config);
    }


}
