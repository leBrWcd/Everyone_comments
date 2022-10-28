package com.rrdp.interceptor;/**
 * @author lebrwcd
 * @date 2022/10/28
 * @note
 */

import com.rrdp.dto.UserDTO;
import com.rrdp.entity.User;
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

        // 1.从session中获取用户
        Object user = request.getSession().getAttribute("user");
        // 2.判断用户是否存在
        if (user == null) {
            // 不存在，拦截 401 未授权
            response.setStatus(401);
            return false;
        }
        // 3. 存在，保存用户到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        // 4.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户,ThreadLocal需要手动移除，不然会导致内存泄漏
        UserHolder.removeUser();
    }
}
