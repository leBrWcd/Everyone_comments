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
import com.rrdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.rrdp.utils.redis.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author lebrwcd
 * @since 2022/10/28
 */
@Slf4j
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

    @Override
    public Result logout(HttpServletRequest request) {

        // 获取当前用户
        UserDTO user = UserHolder.getUser();
        String token = request.getHeader("authorization");
        //ThreadLocal删除
        if (user == null) {
            stringRedisTemplate.delete(LOGIN_TOKEN_PRE + token);
            return Result.ok();
        }
        UserHolder.removeUser();
        // redis中token删除
        stringRedisTemplate.delete(LOGIN_TOKEN_PRE + token);
        return Result.ok();
    }


    @Override
    public Result sign() {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取年月
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // 3.拼接key
        String key = USER_SIGN_KEY + userId + format;
        // 4.当天签到是该月的第几号
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入redis的bitMap  setbit key day 1
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth - 1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获得当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 获取日期
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // 获取redis中当前用户该月截至今天的签到记录
        String key = USER_SIGN_KEY + userId + format;
        List<Long> bitField = stringRedisTemplate.opsForValue().bitField
                (key, BitFieldSubCommands
                        .create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(now.getDayOfMonth())).valueAt(0));
        if (bitField == null || bitField.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        // 当月记录数bitMap的十进制数
        Long num = bitField.get(0);
        if (num == 0) {
            return Result.ok(0);
        }
        log.debug("num == {}",num);
        // 获得的bitMap最后一位与1做与运算，没运算完一位，bitMap右移继续
        int count = 0;
        while (true) {
            if ((num & 1) == 0) {
                break;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
            count++;
        }
        return Result.ok(count);
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
