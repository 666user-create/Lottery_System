package org.example.lottery_system;

import org.example.lottery_system.common.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
public class RedisTest {
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void redisTest(){
        stringRedisTemplate.opsForValue().set("key1","value1");
        System.out.println(stringRedisTemplate.opsForValue().get("key1"));
    }
    @Test
    void redisUtilTest(){
        redisUtil.set("44","88");
        redisUtil.set("55","99",60L);
        System.out.println(redisUtil.hasKey("44"));
        System.out.println(redisUtil.hasKey("55"));
        System.out.println(redisUtil.hasKey("66"));

        System.out.println(redisUtil.get("44"));
        System.out.println(redisUtil.get("55"));
        System.out.println(redisUtil.get("66"));

        System.out.println(redisUtil.delete("44","55"));
        System.out.println(redisUtil.hasKey("44"));
        System.out.println(redisUtil.hasKey("55"));
    }
}
