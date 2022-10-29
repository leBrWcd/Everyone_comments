package com.rrdp.interceptor;/**
 * @author lebrwcd
 * @date 2022/10/29
 * @note
 */

import cn.hutool.core.bean.BeanUtil;
import com.rrdp.dto.UserDTO;
import com.rrdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.rrdp.utils.SystemConstants.LOGIN_TOKEN_PRE;
import static com.rrdp.utils.SystemConstants.LOGIN_TOKEN_TTL;

/**
 * ClassName RefreshTokenInterceptor
 * Description 得到用户保存到ThreadLocal并且刷新token
 *
 * @author lebr7wcd
 * @version 1.0
 * @date 2022/10/29
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1.从请求头中获取token'
        String token = request.getHeader("authorization");
        if (StringUtils.isEmpty(token)) {
            // token不存在，拦截 401 未授权
            response.setStatus(401);
            return false;
        }
        // 2.基于token获取redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_TOKEN_PRE + token);
        // 3.判断用户是否存在
        if (userMap.isEmpty()) {
            // 不存在，拦截 401 未授权
            response.setStatus(401);
            return false;
        }
        // 4.将查询都的map转为dto
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
        // 5.刷新token有效期
        stringRedisTemplate.expire(LOGIN_TOKEN_PRE + token,LOGIN_TOKEN_TTL, TimeUnit.MINUTES);
        // 6.保存用户到ThreadLocal
        UserHolder.saveUser(userDTO);
        // 7.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户,ThreadLocal需要手动移除，不然会导致内存泄漏
        UserHolder.removeUser();
    }

}
