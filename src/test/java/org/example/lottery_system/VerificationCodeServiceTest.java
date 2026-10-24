package org.example.lottery_system;

import org.example.lottery_system.service.VerificationCodeService;
import org.example.lottery_system.service.enums.VerificationSceneEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class VerificationCodeServiceTest {
    @Autowired
    private VerificationCodeService verificationCodeService;

    @Test
    public void testSendVerificationCode() {
        verificationCodeService.sendVerificationCode("13799948148", VerificationSceneEnum.LOGIN);
        System.out.println(verificationCodeService.getVerificationCode("13799948148", VerificationSceneEnum.LOGIN));
    }

    /**
     * 场景隔离回归：REGISTER/LOGIN 与 SECURITY 的 key 必须不同，
     * 否则登录码可直接用于改密码（本次改造的核心目的）。
     */
    @Test
    public void testSceneKeysAreIsolated() {
        String phone = "13799948148";
        assertFalse(VerificationSceneEnum.LOGIN.keyOf(phone)
                        .equals(VerificationSceneEnum.SECURITY.keyOf(phone)),
                "登录场景与账号安全场景的 Redis key 不能相同");
        assertTrue(VerificationSceneEnum.LOGIN.keyOf(phone).endsWith(phone));
        assertTrue(VerificationSceneEnum.SECURITY.keyOf(phone).endsWith(phone));
    }

    /** 恒定时间比较：空值、长度不等、内容不同都必须判为不匹配 */
    @Test
    public void testMatches() {
        assertTrue(VerificationCodeService.matches("1234", "1234"));
        assertFalse(VerificationCodeService.matches("1234", "1235"));
        assertFalse(VerificationCodeService.matches("1234", "12345"));
        assertFalse(VerificationCodeService.matches(null, "1234"));
        assertFalse(VerificationCodeService.matches("1234", null));
        assertFalse(VerificationCodeService.matches("", "1234"));
    }
}
