package org.example.lottery_system;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.controller.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防退化用例：业务拒绝 vs 服务端故障的日志级别。
 *
 * <p>缺陷背景：{@code GlobalExceptionHandler.serviceException()} 原先对所有 {@code ServiceException}
 * 一律 {@code logger.error("服务异常", e)}。而其中「邮箱已注册」「验证码错误」这类是**用户输入不合法**
 * 的预期拒绝，服务端无故障，却会产生 ERROR + 完整堆栈：
 * <pre>
 * ERROR o.e.l.c.h.GlobalExceptionHandler - 服务异常
 * org.example.lottery_system.common.exception.ServiceException: 邮箱已注册
 *     at ...UserServiceImpl.checkRegisterInfo(UserServiceImpl.java:214)
 * </pre>
 * 后果：生产日志被无效 ERROR 淹没、以 ERROR 计数/告警的监控持续误报、真实故障被掩盖。
 *
 * <p>本用例为**纯单元测试**（不启动 Spring、不连库）：
 * 直接调用 handler 方法，并用内存 Logback Appender 断言真实产生的日志级别与是否附带堆栈。
 * 若有人把逻辑改回「一律 ERROR」，本用例必须失败。
 */
class GlobalExceptionHandlerLogLevelTest {

    /** 被测对象：无依赖，可直接 new */
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private Logger handlerLogger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        originalLevel = handlerLogger.getLevel();
        // 保证捕获到 WARN（若根配置把本 logger 设为 ERROR 会漏记）
        handlerLogger.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        handlerLogger.detachAppender(appender);
        handlerLogger.setLevel(originalLevel);
    }

    private ILoggingEvent only() {
        List<ILoggingEvent> events = appender.list;
        assertEquals(1, events.size(), "应恰好产生 1 条日志，实际: " + events.size());
        return events.get(0);
    }

    @Test
    @DisplayName("业务拒绝（106 邮箱已注册）→ WARN 且不带堆栈，且对外 code 不变")
    void businessRejectionLoggedAsWarnWithoutStack() {
        var result = handler.serviceException(new ServiceException(106, "邮箱已注册"));

        ILoggingEvent event = only();
        assertEquals(Level.WARN, event.getLevel(), "预期业务拒绝应为 WARN，不应是 ERROR");
        assertFalse(event.getLevel() == Level.ERROR, "业务拒绝不得记为 ERROR");
        assertTrue(event.getFormattedMessage().contains("106"),
                "WARN 文案应包含错误码便于检索，实际: " + event.getFormattedMessage());
        assertTrue(event.getFormattedMessage().contains("邮箱已注册"),
                "WARN 文案应包含业务原因，实际: " + event.getFormattedMessage());
        assertFalse(event.getThrowableProxy() != null && event.getThrowableProxy().getStackTraceElementProxyArray().length > 0,
                "业务拒绝不应打印堆栈（原缺陷即打印了 50+ 行堆栈）");

        // 边界：日志级别变更不得影响对外返回契约
        assertNotNull(result);
        assertEquals(106, result.getCode(), "对外错误码必须保持 106（仅日志级别变化）");
        assertEquals("邮箱已注册", result.getMessage());
    }

    @Test
    @DisplayName("其他业务拒绝码（101/107/110/111/112/115/118/201/206/300/400~402/500）→ 一律 WARN")
    void allExpectedBusinessCodesAreWarn() {
        // 注意：不含 200 —— CommonResult.error 用 200 表示成功，会抛「code正常」断言，
        // 因此 200 不可能作为 ServiceException 的错误码到达异常处理器。
        int[] codes = {101, 107, 110, 111, 112, 115, 118, 201, 206, 300, 400, 401, 402, 500};
        for (int code : codes) {
            appender.list.clear();
            handler.serviceException(new ServiceException(code, "业务码-" + code));
            ILoggingEvent event = only();
            assertEquals(Level.WARN, event.getLevel(), "错误码 " + code + " 应为 WARN（预期业务拒绝）");
        }
    }

    @Test
    @DisplayName("服务端真实故障（未登记错误码）→ 仍为 ERROR 且带堆栈")
    void systemFaultStillLoggedAsErrorWithStack() {
        var result = handler.serviceException(new ServiceException(9999, "未登记的未知故障"));

        ILoggingEvent event = only();
        assertEquals(Level.ERROR, event.getLevel(), "未登记错误码应视为服务端故障，记为 ERROR");
        assertNotNull(event.getThrowableProxy(), "系统故障必须保留完整堆栈便于定位");

        assertEquals(9999, result.getCode(), "对外错误码不应被日志逻辑改写");
    }
}
