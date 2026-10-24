package org.example.lottery_system.controller.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 账号安全页所需的个人信息。
 *
 * 只包含本人、且只对本人返回的数据：
 *   - email              当前邮箱（用于弹窗回填，让用户确认要改的是哪个）
 *   - maskedPhoneNumber  脱敏手机号（138****8000），用于提示「验证码将发送至」
 *
 * 手机号只回脱敏值，明文手机号由服务端根据 token 反查，永远不出响应体。
 */
@Data
public class AccountSecurityResult implements Serializable {
    //当前邮箱
    private String email;
    //脱敏手机号
    private String maskedPhoneNumber;
}
