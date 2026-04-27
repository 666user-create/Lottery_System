package org.example.lottery_system.service;

public interface VerificationCodeService {
    //发送验证码
    boolean sendVerificationCode(String phoneNumber);

    //从缓存中获取验证码
    String getVerificationCode(String phoneNumber);

    /** 校验通过后删除，避免同一验证码在有效期内被重复使用 */
    void clearVerificationCode(String phoneNumber);
}
