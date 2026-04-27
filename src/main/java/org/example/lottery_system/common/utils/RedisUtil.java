package org.example.lottery_system.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {
    private static final Logger logger = LoggerFactory.getLogger(RedisUtil.class);
    /**
     * RedisTemplate: 通用的 Redis 模板，支持多种数据类型,被转换成字节数组(不可读)
     * StringRedisTemplate: 字符串类型的 RedisTemplate,直接操作字符串(可读)
     */

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 设置键值对
    public boolean set(String key, String value) {
        try {
            stringRedisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            logger.error("RedisUtil set error({},{})", key, value, e);
            return false;
        }
    }

    // 获取键值对
    public String get(String key) {
        try {
            return StringUtils.hasText(stringRedisTemplate.opsForValue().get(key))
                    ? stringRedisTemplate.opsForValue().get(key) : null;
        } catch (Exception e) {
            logger.error("RedisUtil get error({})", key, e);
            return null;
        }
    }

    //设置过期时间
    public boolean set(String key, String value, Long time) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            logger.error("RedisUtil set error({},{},{})", key, value, time, e);
            return false;
        }
    }

    //删除键值对
    public boolean delete(String... key) {
        try {
            if (key != null && key.length > 0) {
                if (key.length == 1) stringRedisTemplate.delete(key[0]);
                else {
                    stringRedisTemplate.delete(
                            (Collection<String>) CollectionUtils.arrayToList(key)
                    );
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("RedisUtil delete error({})", key, e);
            return false;
        }
    }

    //判断键值对是否存在
    public boolean hasKey(String key) {
        try {
            return StringUtils.hasText(key) && stringRedisTemplate.hasKey(key);
        } catch (Exception e) {
            logger.error("RedisUtil hasKey error({})", key, e);
            return false;
        }
    }

    public Long increment(String key) {
        try {
            return stringRedisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            logger.error("RedisUtil increment error({})", key, e);
            return null;
        }
    }

    public void expire(String key, long seconds) {
        try {
            if (StringUtils.hasText(key)) {
                stringRedisTemplate.expire(key, seconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            logger.error("RedisUtil expire error({},{})", key, seconds, e);
        }
    }
}
