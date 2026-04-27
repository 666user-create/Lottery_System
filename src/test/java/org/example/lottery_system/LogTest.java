package org.example.lottery_system;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class LogTest {
    private static final Logger logger= LoggerFactory.getLogger(LogTest.class);
    @Test
    void test() {
        System.out.println("test");
        logger.info("test");
    }
}
