package org.example.lottery_system.service;

import org.example.lottery_system.service.enums.VerificationSceneEnum;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public interface VerificationCodeService {
    /**
     * 发送验证码。
     * 场景由调用方（服务端）固定指定，不接受客户端传入。
     */
    boolean sendVerificationCode(String phoneNumber, VerificationSceneEnum scene);

    /** 从缓存中获取指定场景的验证码 */
    String getVerificationCode(String phoneNumber, VerificationSceneEnum scene);

    /** 校验通过后删除，避免同一验证码在有效期内被重复使用 */
    void clearVerificationCode(String phoneNumber, VerificationSceneEnum scene);

    /**
     * 校验用户输入的验证码是否与缓存中的一致。
     *
     * 用 MessageDigest.isEqual 做恒定时间比较，避免逐字符 equals 提前返回
     * 所泄露的时序信息（理论上可被用来逐位猜解验证码）。
     * 验证码只有 4 位，本地网络下时序攻击并不现实，但这里的成本几乎为零。
     */
    static boolean matches(String inputCode, String cachedCode) {
        if (!StringUtils.hasText(inputCode) || !StringUtils.hasText(cachedCode)) {
            return false;
        }
        return MessageDigest.isEqual(
                inputCode.getBytes(StandardCharsets.UTF_8),
                cachedCode.getBytes(StandardCharsets.UTF_8));
    }
}
