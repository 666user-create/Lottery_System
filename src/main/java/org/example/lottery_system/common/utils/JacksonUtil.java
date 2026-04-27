package org.example.lottery_system.common.utils;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lottery_system.common.pojo.CommonResult;
import org.springframework.util.ReflectionUtils;

import java.util.List;
import java.util.concurrent.Callable;

public class JacksonUtil {
    /*
     * 单例
     * */
    private JacksonUtil() {
    }

    private final static ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
    }

    private static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    //封装tryParse方法，用于解析Json字符串
    private static <T> T tryParse(Callable<T> parser) {
        return tryParse(parser, JacksonException.class);
    }

    private static <T> T tryParse(Callable<T> parser, Class<? extends Exception> check) {
        try {
            return (T) parser.call();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /*
     * 序列化方法
     * */
    public static String writeValueAsString(Object value) {
        return JacksonUtil.tryParse(() ->
                JacksonUtil.getObjectMapper().writeValueAsString(value));
    }

    /*
     * 反序列化方法
     * */
    @SuppressWarnings("unchecked")
    public static <T> T readValue(String content, Class<?> valueType) {
        return (T) JacksonUtil.tryParse(() ->
                JacksonUtil.getObjectMapper().readValue(content, valueType));
    }
    /*
    * List反序列化方法
    * */
    @SuppressWarnings("unchecked")
    public static <T> List<T> readListValue(String content, Class<?> paramClass) {
        JavaType javaType=JacksonUtil.getObjectMapper().getTypeFactory()
                .constructParametricType(List.class, paramClass);
        return (List<T>) JacksonUtil.tryParse(() ->
                JacksonUtil.getObjectMapper().readValue(content, javaType));
    }
}