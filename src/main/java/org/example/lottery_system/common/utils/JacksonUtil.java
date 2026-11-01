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

    /*
     * ==================== 敏感入参脱敏约定（2026-11-02，勿回退）====================
     *
     * 本项目的 controller 普遍用 `logger.info("...:{}", JacksonUtil.writeValueAsString(param))`
     * 打印入参。曾经因此把明文密码/验证码写进日志（实测
     * `userRegister param:{"password":"123456",...}`），与「操作日志刻意不记录
     * 请求体以防泄露密码」的设计自相矛盾。
     *
     * 约定：任何含凭证的 Param 类，其敏感字段必须同时加两个注解
     *   1) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
     *      —— 挡住 writeValueAsString 这条 Jackson 路径（只反序列化，不序列化）
     *   2) @ToString.Exclude
     *      —— 挡住 `log.info("{}", param)` 这条 Lombok toString 路径
     *
     * 只加其中一个仍会泄露：两条打印路径在本项目里并存。
     * 已按此约定处理的类：UserRegisterParam、UserPasswordLoginParam、
     * UserShortMessageLoginParam、AccountVerifyParam、UpdatePasswordParam。
     *
     * 防退化测试：src/test/.../SensitiveParamLoggingTest.java
     * （新增含凭证的 Param 类时，请在该测试里补一条断言）
     *
     * 注意：不要试图在这里给全局 ObjectMapper 加字段名黑名单——
     * 那样会让 JacksonUtil 无法序列化任何含 password 字段的正常数据结构，
     * 且无法覆盖 toString 路径。逐类标注才是可控做法。
     * ==========================================================================
     */

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