package com.waimai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.waimai.common.entity.User;
import com.waimai.common.exception.BusinessException;
import com.waimai.common.utils.BCryptUtil;
import com.waimai.service.mapper.UserMapper;
import com.waimai.service.service.UserService;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final String SMS_CODE_PREFIX = "waimai:sms:";
    private static final long SMS_CODE_TTL_SECONDS = 300; // 5 minutes

    private final RedisTemplate<String, Object> redisTemplate;

    public UserServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public User loginByWechat(String openid, String nickname, String avatar) {
        User user = getByOpenid(openid);
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname(nickname);
            user.setAvatar(avatar);
            user.setStatus(1);
            save(user);
        }
        return user;
    }

    @Override
    public User getByOpenid(String openid) {
        return lambdaQuery().eq(User::getOpenid, openid).one();
    }

    @Override
    public User getByPhone(String phone) {
        return lambdaQuery().eq(User::getPhone, phone).one();
    }

    @Override
    public User registerByPassword(String phone, String password, String nickname) {
        User exist = getByPhone(phone);
        if (exist != null) {
            throw new BusinessException("该手机号已注册，请直接登录");
        }
        User user = new User();
        user.setOpenid("pwd_" + phone);
        user.setPhone(phone);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setNickname(nickname != null && !nickname.isBlank() ? nickname : "用户" + phone.substring(phone.length() - 4));
        user.setStatus(1);
        save(user);
        return user;
    }

    @Override
    public User loginByPassword(String phone, String password) {
        User user = getByPhone(phone);
        if (user == null) {
            throw new BusinessException("手机号未注册");
        }
        if (user.getPassword() == null || !BCryptUtil.checkPassword(password, user.getPassword())) {
            throw new BusinessException("密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系平台");
        }
        return user;
    }

    @Override
    public void sendSmsCode(String phone) {
        String code = String.format("%06d", new java.util.Random().nextInt(999999));
        redisTemplate.opsForValue().set(SMS_CODE_PREFIX + phone, code, SMS_CODE_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("=== SMS verification code for {}: {} ===", phone, code);
    }

    @Override
    public boolean verifySmsCode(String phone, String code) {
        String stored = (String) redisTemplate.opsForValue().get(SMS_CODE_PREFIX + phone);
        return stored != null && stored.equals(code);
    }
}
