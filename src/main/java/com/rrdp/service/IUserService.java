package com.rrdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rrdp.dto.LoginFormDTO;
import com.rrdp.dto.Result;
import com.rrdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author lebrwcd
 * @since 2022/10/28
 */
public interface IUserService extends IService<User> {

    /**
     * 发送手机验证码
     * @param phone 手机号
     * @param session session
     * @return Result
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录功能
     * @param loginForm 登录表单
     * @param session session
     * @return Result
     */
    Result login(LoginFormDTO loginForm, HttpSession session);
}
