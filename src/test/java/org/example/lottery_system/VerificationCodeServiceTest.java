package org.example.lottery_system;

import org.example.lottery_system.service.VerificationCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class VerificationCodeServiceTest {
    @Autowired
    private VerificationCodeService verificationCodeService;

    @Test
    public void testSendVerificationCode() {
        verificationCodeService.sendVerificationCode("13799948148");
        System.out.println(verificationCodeService.getVerificationCode("13799948148"));
    }
}
