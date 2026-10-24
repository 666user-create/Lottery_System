package org.example.lottery_system.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 验证码用途场景。
 *
 * 背景：登录验证码与账号安全（改邮箱/改密码）验证码原先共用同一个 Redis key
 * （verification_code:&lt;手机号&gt;），等于「能登录」的凭证可以直接升级成「能改凭证」的权限。
 * 攻击者只要通过钓鱼拿到一次登录验证码，就能改掉受害者的邮箱与密码。
 *
 * 现按场景拆分 key 前缀，登录码无法用于改密/改邮箱，反之亦然。
 * 注意：场景由服务端根据端点固定指定，**绝不接受客户端传入**，否则隔离形同虚设。
 */
@Getter
@AllArgsConstructor
public enum VerificationSceneEnum {
    /** 登录（/user/verification-code/send → /user/message/login） */
    LOGIN("verification_code:login:"),
    /** 账号安全：修改邮箱 / 修改密码（/user/security/verification-code/send） */
    SECURITY("verification_code:security:");

    private final String keyPrefix;

    public String keyOf(String phoneNumber) {
        return keyPrefix + phoneNumber;
    }
}
