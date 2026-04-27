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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {
    private static final Logger log = LoggerFactory.getLogger(VerificationCodeServiceImpl.class);
    //给redis的key定义前缀
    private static final String VERIFICATION_CODE_KEY_PREFIX = "verification_code:";
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
    public boolean sendVerificationCode(String phoneNumber) {
        //校验手机号
        if(!RegexUtil.checkMobile(phoneNumber)){
            throw new ServiceException(ServiceErrorCodeConstants.PHONE_ERROR);
        }
        UserDO user = userMapper.selectByPhoneNumber(new Encrypt(phoneNumber));
        if (user == null) {
            throw new ServiceException(ServiceErrorCodeConstants.PHONE_ERROR);
        }
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
            log.info("验证码已发起短信: {}", phoneNumber);
        } catch (Exception e) {
            log.warn("验证码发送失败: {}", phoneNumber, e);
            throw new ServiceException(ServiceErrorCodeConstants.SMS_SEND_FAILED);
        }
        boolean stored = redisUtil.set(VERIFICATION_CODE_KEY_PREFIX+phoneNumber,code,60*5L);
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
    public String getVerificationCode(String phoneNumber) {
        //校验手机号
        if(!RegexUtil.checkMobile(phoneNumber)){
            throw new ServiceException(ServiceErrorCodeConstants.PHONE_ERROR);
        }
        //获取手机号
        return redisUtil.get(VERIFICATION_CODE_KEY_PREFIX+phoneNumber);
    }

    @Override
    public void clearVerificationCode(String phoneNumber) {
        if (!RegexUtil.checkMobile(phoneNumber)) {
            return;
        }
        redisUtil.delete(VERIFICATION_CODE_KEY_PREFIX + phoneNumber);
    }
}
