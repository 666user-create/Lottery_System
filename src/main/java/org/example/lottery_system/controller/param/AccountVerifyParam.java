package org.example.lottery_system.controller.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改邮箱/密码的验证信息。
 *
 * verifyType 取值：
 *   password —— 输入原密码验证（oldPassword 必填）
 *   code     —— 使用登录时的短信验证码验证（verificationCode 必填）
 *   secret   —— 使用管理员口令验证（adminSecret 必填，仅 ADMIN 可用）
 *
 * 注意：这里**不接受**客户端传入手机号/邮箱作为验证目标。
 * 验证目标一律由服务端根据 token 中的 userId 反查本人手机号，
 * 否则攻击者只需猜中他人手机号收到的验证码，即可改掉他人账号的邮箱与密码。
 *
 * 三种方式都只能改**当前登录者本人**的邮箱/密码，userId 取自 token，不接受入参指定他人。
 *
 * 关于 verifyType=secret：
 * 口令是所有管理员共用的常量（见 application.properties 的 admin.secret），不是个人因素。
 * 它解决的是「管理员已登录但忘记密码 / 收不到短信」的自救场景。
 * 代价是信任等级低于原密码与本人手机——但该会话本就已是管理员权限，
 * 能造成的破坏远大于改自己凭证，故取舍上可接受。
 */
@Data
public class AccountVerifyParam {

    @NotBlank(message = "验证方式不能为空")
    private String verifyType;

    /** verifyType=password 时必填 */
    private String oldPassword;

    /** verifyType=code 时必填 */
    private String verificationCode;

    /** verifyType=secret 时必填（仅管理员可用） */
    private String adminSecret;
}
