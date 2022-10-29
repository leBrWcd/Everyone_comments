package com.rrdp.interceptor;/**
 * @author lebrwcd
 * @date 2022/10/28
 * @note
 */

import com.rrdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * ClassName LoginInterceptor
 * Description 登录拦截器
 *
 * @author lebr7wcd
 * @version 1.0
 * @date 2022/10/28
 */
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 判断ThreadLocal中是否存在用户
        if (UserHolder.getUser() == null) {
            // 拦截
            return false;
        }
        // 放行
        return true;
    }
}
