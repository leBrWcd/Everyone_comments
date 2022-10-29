package com.rrdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rrdp.dto.LoginFormDTO;
import com.rrdp.dto.Result;
import com.rrdp.dto.UserDTO;
import com.rrdp.entity.User;
import com.rrdp.mapper.UserMapper;
import com.rrdp.service.IUserService;
import com.rrdp.utils.RegexUtils;
import com.rrdp.utils.SystemConstants;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.rrdp.utils.SystemConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author lebrwcd
 * @since 2022/10/28
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        // 1.1 手机号不符合，返回错误信息
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("请输入正确的手机号");
        }
        // 2.校验成功，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 3.保存验证码到Redis   SET KEK VALUE EX 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_PRE + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4.发送验证码，需要调用第三方API，我们采用假验证的方式
        log.debug("发送手机验证码成功，验证码: [ " + code +" ]");
        // 5.返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号和验证码
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 1.1 不一致返回错误信息
        String phoneCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_PRE + phone);
        if (phoneCode == null || !phoneCode.equals(code)) {
            return Result.fail("验证码失效");
        }
        // 2.根据手机号查询用户是否存在 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
        // 2.1 不存在，新建用户到数据库，接着保存用户到session
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        // 3. 保存用户信息到redis中
        // 3.1 生成随机token，作为user存入redis的key
        String token = UUID.randomUUID().toString(true);
        // 3.2 将user对象转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        // 确保每个字段都是字符串
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 3.3 保存到redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_TOKEN_PRE + token,userMap);
        // 3.4 设置有效期
        stringRedisTemplate.expire(LOGIN_TOKEN_PRE + token,LOGIN_TOKEN_TTL,TimeUnit.MINUTES);
        // 4. 返回token给客户端
        return Result.ok(token);
    }

    /**
     * 根据手机号创建新用户
     * @param phone 手机号
     * @return 用户
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 新增用户到数据库
        baseMapper.insert(user);
        return user;
    }
}
