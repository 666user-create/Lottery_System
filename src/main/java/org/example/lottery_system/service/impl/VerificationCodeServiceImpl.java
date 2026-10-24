package org.example.lottery_system.service.impl;

import org.example.lottery_system.common.errorcode.ServiceErrorCodeConstants;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.common.utils.CaptchaUtil;
import org.example.lottery_system.common.utils.RedisUtil;
import org.example.lottery_system.common.utils.RegexUtil;
import org.example.lottery_system.common.utils.SMSUtil;
import org.example.lottery_system.dao.dataobject.Encrypt;
import org.example.lottery_system.dao.dataobject.UserDO;
import org.example.lottery_system.dao.mapper.UserMapper;
import org.example.lottery_system.service.VerificationCodeService;
import org.example.lottery_system.service.enums.VerificationSceneEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {
    private static final Logger log = LoggerFactory.getLogger(VerificationCodeServiceImpl.class);
    /*
     * 验证码 key 前缀由 VerificationSceneEnum 提供（登录 / 账号安全 各自独立）。
     *
     * 限流 key 则**按手机号共用**，不区分场景：
     * 登录与改密码若各算一份配额，攻击者换个场景就能绕过 60 秒冷却与每小时 5 条上限，
     * 短信费用与骚扰风险都会翻倍。共用后，任一场景发过码，另一场景同样受冷却约束。
     * */
    private static final String SMS_SEND_COOLDOWN_PREFIX = "sms_send_cooldown:";
    private static final String SMS_SEND_HOUR_COUNT_PREFIX = "sms_send_hour_count:";
    private static final long SMS_COOLDOWN_SECONDS = 60L;
    private static final long SMS_HOUR_WINDOW_SECONDS = 3600L;
    private static final int SMS_MAX_PER_HOUR = 5;

    @Autowired
    private SMSUtil smsUtil;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private UserMapper userMapper;

    @Override
    public boolean sendVerificationCode(String phoneNumber, VerificationSceneEnum scene) {
        //校验手机号
        if(!RegexUtil.checkMobile(phoneNumber)){
            throw new ServiceException(ServiceErrorCodeConstants.PHONE_ERROR);
        }
        UserDO user = userMapper.selectByPhoneNumber(new Encrypt(phoneNumber));
        // 修复 BUG-005：区分「手机号未注册」(118) 与「已注册但身份缺失」(114)
        // 原实现将两种情况统一返回 114"该手机号未登记为管理员账号"，
        // 实测已注册普通用户可正常发送（功能正常），问题在于未注册场景的文案误导
        if (user == null) {
            throw new ServiceException(ServiceErrorCodeConstants.PHONE_NOT_REGISTERED);
        }
        if (!StringUtils.hasText(user.getIdentity())) {
            throw new ServiceException(ServiceErrorCodeConstants.PHONE_NOT_ADMIN);
        }
        // 限流按手机号共用，与场景无关（理由见类顶部注释）
        if (redisUtil.hasKey(SMS_SEND_COOLDOWN_PREFIX + phoneNumber)) {
            throw new ServiceException(ServiceErrorCodeConstants.SMS_SEND_TOO_FREQUENT);
        }
        String hourKey = SMS_SEND_HOUR_COUNT_PREFIX + phoneNumber;
        String hourCountStr = redisUtil.get(hourKey);
        int hourCount = 0;
        if (StringUtils.hasText(hourCountStr)) {
            try {
                hourCount = Integer.parseInt(hourCountStr);
            } catch (NumberFormatException ignored) {
                hourCount = 0;
            }
        }
        if (hourCount >= SMS_MAX_PER_HOUR) {
            throw new ServiceException(ServiceErrorCodeConstants.SMS_SEND_HOUR_LIMIT);
        }
        //生成随机验证码
        String code= CaptchaUtil.generateCaptchaCode(4);
        //发送验证码
        try {
            smsUtil.send(phoneNumber,code);
            log.info("验证码已发起短信: {} 场景: {}", phoneNumber, scene);
        } catch (Exception e) {
            log.warn("验证码发送失败: {}", phoneNumber, e);
            throw new ServiceException(ServiceErrorCodeConstants.SMS_SEND_FAILED);
        }
        // 按场景写入独立 key，登录码与账号安全码互不通用
        boolean stored = redisUtil.set(scene.keyOf(phoneNumber), code, 60*5L);
        if (!stored) {
            throw new ServiceException(ServiceErrorCodeConstants.SMS_SEND_FAILED);
        }
        Long afterIncr = redisUtil.increment(hourKey);
        if (afterIncr != null && afterIncr == 1L) {
            redisUtil.expire(hourKey, SMS_HOUR_WINDOW_SECONDS);
        }
        redisUtil.set(SMS_SEND_COOLDOWN_PREFIX + phoneNumber, "1", SMS_COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public String getVerificationCode(String phoneNumber, VerificationSceneEnum scene) {
        //校验手机号
        if(!RegexUtil.checkMobile(phoneNumber)){
            throw new ServiceException(ServiceErrorCodeConstants.PHONE_ERROR);
        }
        //获取手机号
        return redisUtil.get(scene.keyOf(phoneNumber));
    }

    @Override
    public void clearVerificationCode(String phoneNumber, VerificationSceneEnum scene) {
        if (!RegexUtil.checkMobile(phoneNumber)) {
            return;
        }
        redisUtil.delete(scene.keyOf(phoneNumber));
    }
}
