package org.example.lottery_system;

import org.example.lottery_system.dao.dataobject.Encrypt;
import org.example.lottery_system.dao.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SqlTest {
    @Autowired
    private UserMapper userMapper;
    @Test
    void mailCount() {
        System.out.println(userMapper.countByMail("123@qq.com"));
    }

    @Test
    void phoneCount() {
        System.out.println(userMapper.countByPhoneNumber(new Encrypt("13799948148")));
    }
}
