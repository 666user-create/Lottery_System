package org.example.lottery_system;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lottery_system.common.utils.JacksonUtil;
import org.example.lottery_system.controller.param.AccountVerifyParam;
import org.example.lottery_system.controller.param.UpdateEmailParam;
import org.example.lottery_system.controller.param.UpdatePasswordParam;
import org.example.lottery_system.controller.param.UserPasswordLoginParam;
import org.example.lottery_system.controller.param.UserRegisterParam;
import org.example.lottery_system.controller.param.UserShortMessageLoginParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感入参脱敏的防退化测试。
 *
 * <p>背景（2026-11-02 实测缺陷）：UserController 用
 * {@code JacksonUtil.writeValueAsString(param)} 打印入参，导致
 * {@code userRegister param:{"password":"123456", ...}} 这类明文凭证直接落进日志，
 * 与「操作日志刻意不记录请求体以防泄露密码」的设计自相矛盾。
 *
 * <p>修复手段是给敏感字段加两个注解：
 * <ul>
 *   <li>{@code @JsonProperty(access = WRITE_ONLY)} —— Jackson 序列化时排除；</li>
 *   <li>{@code @ToString.Exclude} —— 挡住 {@code log.info("{}", param)} 的隐式 toString。</li>
 * </ul>
 *
 * <p>本测试同时覆盖**两条打印路径**，因为项目里两种写法并存：
 * 只封住 Jackson 或只封住 toString 都会留下泄露缺口。
 * 任何一条断言变红，都说明有人删掉了脱敏注解或在新增的 Param 类里漏加了
 * ——这正是需要被拦下的回归。
 *
 * <p>设计取舍：不启动 Spring 容器（无 {@code @SpringBootTest}），
 * 因此不依赖 MySQL/Redis/RabbitMQ，可在任何环境秒级跑完。
 */
class SensitiveParamLoggingTest {

    /** 用一个足够独特的标记串，避免与正常文本偶然相撞 */
    private static final String SECRET_MARK = "SECRET_MARK_7x9q";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==================== 路径一：Jackson 序列化（writeValueAsString） ====================

    @Test
    @DisplayName("注册参数序列化不含 password（UserController 的日志路径）")
    void registerParamSerializationHidesPassword() {
        UserRegisterParam param = new UserRegisterParam();
        param.setName("张三");
        param.setMail("mask@test.com");
        param.setPhoneNumber("13800001111");
        param.setIdentity("NORMAL");
        param.setPassword(SECRET_MARK);

        String json = JacksonUtil.writeValueAsString(param);

        assertFalse(json.contains(SECRET_MARK),
                "注册参数序列化后仍含明文密码，日志会泄露：" + json);
        assertFalse(json.contains("password"),
                "序列化结果不应出现 password 字段名：" + json);
        // 非敏感字段必须保留，否则脱敏过度、日志失去排查价值
        assertTrue(json.contains("mask@test.com"),
                "非敏感字段应保留：" + json);
    }

    @Test
    @DisplayName("密码登录参数序列化不含 password")
    void passwordLoginParamSerializationHidesPassword() {
        UserPasswordLoginParam param = new UserPasswordLoginParam();
        param.setLoginName("mask@test.com");
        param.setPassword(SECRET_MARK);

        String json = JacksonUtil.writeValueAsString(param);

        assertFalse(json.contains(SECRET_MARK), "登录参数序列化后仍含明文密码：" + json);
        assertTrue(json.contains("mask@test.com"), "loginName 应保留以便排查：" + json);
    }

    @Test
    @DisplayName("短信验证码登录参数序列化不含 verificationCode")
    void smsLoginParamSerializationHidesCode() {
        UserShortMessageLoginParam param = new UserShortMessageLoginParam();
        param.setLoginMobile("13800001111");
        param.setVerificationCode(SECRET_MARK);

        String json = JacksonUtil.writeValueAsString(param);

        assertFalse(json.contains(SECRET_MARK), "序列化后仍含明文验证码：" + json);
    }

    @Test
    @DisplayName("账号安全参数序列化不含 oldPassword / verificationCode / adminSecret")
    void accountVerifyParamSerializationHidesAllSecrets() {
        AccountVerifyParam param = new AccountVerifyParam();
        param.setVerifyType("password");
        param.setOldPassword(SECRET_MARK);
        param.setVerificationCode(SECRET_MARK);
        param.setAdminSecret(SECRET_MARK);

        String json = JacksonUtil.writeValueAsString(param);

        assertFalse(json.contains(SECRET_MARK),
                "旧密码/验证码/管理员口令任一泄露都会导致凭证外泄：" + json);
        // verifyType 是排查必需的非敏感信息，必须保留
        assertTrue(json.contains("password"),
                "verifyType 的值应保留（注意 password 出现在 verifyType 里是预期）：" + json);
    }

    @Test
    @DisplayName("改密码参数序列化不含 newPassword（继承来的敏感字段也要挡）")
    void updatePasswordParamSerializationHidesNewPassword() {
        UpdatePasswordParam param = new UpdatePasswordParam();
        param.setVerifyType("password");
        param.setOldPassword(SECRET_MARK);
        param.setNewPassword(SECRET_MARK);

        String json = JacksonUtil.writeValueAsString(param);

        assertFalse(json.contains(SECRET_MARK), "新旧密码都不应出现在序列化结果：" + json);
    }

    @Test
    @DisplayName("改邮箱参数序列化不含验证凭证")
    void updateEmailParamSerializationHidesVerifySecret() {
        UpdateEmailParam param = new UpdateEmailParam();
        param.setVerifyType("code");
        param.setVerificationCode(SECRET_MARK);
        param.setNewEmail("new-mask@test.com");

        String json = JacksonUtil.writeValueAsString(param);

        assertFalse(json.contains(SECRET_MARK), "验证码不应出现在序列化结果：" + json);
        assertTrue(json.contains("new-mask@test.com"), "新邮箱应保留：" + json);
    }

    // ==================== 路径二：Lombok toString（log.info("{}", param)） ====================

    @Test
    @DisplayName("toString 不含密码（挡住 log.info(\"...:{}\", param) 的泄露）")
    void toStringHidesSecrets() {
        UserRegisterParam register = new UserRegisterParam();
        register.setPassword(SECRET_MARK);

        UserPasswordLoginParam login = new UserPasswordLoginParam();
        login.setPassword(SECRET_MARK);

        AccountVerifyParam verify = new AccountVerifyParam();
        verify.setOldPassword(SECRET_MARK);
        verify.setVerificationCode(SECRET_MARK);
        verify.setAdminSecret(SECRET_MARK);

        UpdatePasswordParam update = new UpdatePasswordParam();
        update.setNewPassword(SECRET_MARK);
        update.setOldPassword(SECRET_MARK);

        UserShortMessageLoginParam sms = new UserShortMessageLoginParam();
        sms.setVerificationCode(SECRET_MARK);

        for (Object param : new Object[]{register, login, verify, update, sms}) {
            String text = String.valueOf(param);
            assertFalse(text.contains(SECRET_MARK),
                    param.getClass().getSimpleName()
                            + " 的 toString 泄露了凭证，log.info(\"{}\", param) 会写进日志：" + text);
        }
    }

    // ==================== 反向校验：脱敏不能破坏反序列化 ====================

    @Test
    @DisplayName("反序列化仍能接收敏感字段（脱敏只影响输出，不影响入参校验）")
    void deserializationStillAcceptsSecrets() throws Exception {
        // 只用原生 ObjectMapper，不依赖容器；关键是证明 WRITE_ONLY 不阻断读取
        String registerJson = "{\"name\":\"张三\",\"mail\":\"a@b.com\","
                + "\"phoneNumber\":\"13800001111\",\"identity\":\"NORMAL\","
                + "\"password\":\"" + SECRET_MARK + "\"}";

        UserRegisterParam parsed = MAPPER.readValue(registerJson, UserRegisterParam.class);
        assertNotNull(parsed.getPassword(),
                "反序列化拿不到密码，登录/注册校验会全部失效");
        assertTrue(SECRET_MARK.equals(parsed.getPassword()),
                "反序列化应还原出原始密码");

        // 再确认一次：读得进来，但再序列化出去仍然不出现
        assertFalse(JacksonUtil.writeValueAsString(parsed).contains(SECRET_MARK),
                "读进来再写出去也不能泄露");
    }
}
