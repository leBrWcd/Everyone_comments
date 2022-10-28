package com.rrdp.config;/**
 * @author lebrwcd
 * @date 2022/10/28
 * @note
 */

import com.rrdp.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
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
