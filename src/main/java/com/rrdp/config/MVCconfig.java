package com.rrdp.config;/**
 * @author lebrwcd
 * @date 2022/10/28
 * @note
 */

import com.rrdp.interceptor.LoginInterceptor;
import com.rrdp.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * ClassName MVCconfig
 * Description MVC配置器
 *
 * @author lebr7wcd
 * @version 1.0
 * @date 2022/10/28
 */
@Configuration
public class MVCconfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截一切请求
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .excludePathPatterns("/user/code","/user/login")
                .addPathPatterns("/**")
                .order(0);
        // 拦截部分请求
        registry.addInterceptor(new LoginInterceptor())
                // 不拦截的
                .excludePathPatterns(
                "/shop/**",
                "/voucher/**",
                "/shop-type/**",
                "/upload/**",
                "/blog/hot",
                "/user/code",
                "/user/login"
                ).order(1);
    }
}
