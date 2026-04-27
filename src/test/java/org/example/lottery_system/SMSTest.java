package org.example.lottery_system;

import org.example.lottery_system.common.utils.SMSUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SMSTest {
    @Autowired
    private SMSUtil smsUtil;
    @Test
    void smsTest() {
        try {
            smsUtil.send("13799948148", "1234");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
