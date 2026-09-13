package org.example.lottery_system.controller.handler;

import org.example.lottery_system.common.errorcode.GlobalErrorCodeConstants;
import org.example.lottery_system.common.exception.ControllerException;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.common.pojo.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.rmi.ServerException;

@RestControllerAdvice // 全局异常处理类
public class GlobalExceptionHandler {
    private static final Logger logger= LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理参数校验异常 (MethodArgumentNotValidException, BindException)
     */
    @ExceptionHandler(value = {MethodArgumentNotValidException.class, BindException.class})
    public CommonResult<?> validationException(Exception e) {
        String message;
        if (e instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            message = methodArgumentNotValidException.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        } else {
            message = ((BindException) e).getBindingResult().getAllErrors().get(0).getDefaultMessage();
        }
        logger.warn("参数校验失败: {}", message);
        return CommonResult.error(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), message);
    }

    /**
     * 处理请求参数缺失异常
     */
    @ExceptionHandler(value = MissingServletRequestParameterException.class)
    public CommonResult<?> missingParameterException(MissingServletRequestParameterException e) {
        logger.warn("缺少请求参数: {}", e.getParameterName());
        return CommonResult.error(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), String.format("缺少请求参数: %s", e.getParameterName()));
    }

    /**
     * 处理请求参数类型不匹配异常
     */
    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class)
    public CommonResult<?> typeMismatchException(MethodArgumentTypeMismatchException e) {
        logger.warn("参数类型不匹配: {} 应为 {}", e.getName(), e.getRequiredType().getName());
        return CommonResult.error(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), String.format("参数类型不匹配: %s", e.getName()));
    }

    @ExceptionHandler(value = ServiceException.class)
    public CommonResult<?> serviceException(ServiceException e) {
        //打印错误日志
        logger.error("服务异常",e);
        //返回失败结果
        return CommonResult.error(e.getCode(),e.getMessage());
    }

    @ExceptionHandler(value = ControllerException.class)
    public CommonResult<?> controllerException(ControllerException e) {
        //打印错误日志
        logger.error("控制器异常",e);
        //返回失败结果
        return CommonResult.error(e.getCode(),e.getMessage());
    }

    /**
     * 请求体不可读（畸形 JSON / 空 body / 类型不匹配）
     * 修复 BUG-009：原实现落到 Exception 兜底 → 500 且回显 Jackson 内部类名与方法签名
     */
    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    public CommonResult<?> messageNotReadableException(HttpMessageNotReadableException e) {
        logger.warn("请求体解析失败: {}", e.getMessage());
        return CommonResult.error(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), "请求体格式错误");
    }

    /**
     * Content-Type 不支持（如 text/plain 调 JSON 接口）
     * 修复 BUG-009：原实现返回 500，应返回 415
     */
    @ExceptionHandler(value = HttpMediaTypeNotSupportedException.class)
    public CommonResult<?> mediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        logger.warn("不支持的 Content-Type: {}", e.getContentType());
        return CommonResult.error(GlobalErrorCodeConstants.UNSUPPORTED_MEDIA_TYPE.getCode(),
                GlobalErrorCodeConstants.UNSUPPORTED_MEDIA_TYPE.getMsg());
    }

    /**
     * 请求方法不支持（如用 GET 调 POST 接口）
     * 修复 BUG-009：原实现返回 500，应返回 405
     */
    @ExceptionHandler(value = HttpRequestMethodNotSupportedException.class)
    public CommonResult<?> methodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        logger.warn("不支持的请求方法: {}", e.getMethod());
        return CommonResult.error(GlobalErrorCodeConstants.METHOD_NOT_ALLOWED.getCode(),
                GlobalErrorCodeConstants.METHOD_NOT_ALLOWED.getMsg());
    }

    /**
     * 静态资源不存在
     * 修复：原实现落到 Exception 兜底 → 以 ERROR 级别打印堆栈，污染日志监控
     * （实测现象：外部探测 /mcp/sse 等路径产生大量 ERROR 日志）
     */
    @ExceptionHandler(value = NoResourceFoundException.class)
    public CommonResult<?> noResourceFoundException(NoResourceFoundException e) {
        logger.warn("资源不存在: {}", e.getResourcePath());
        return CommonResult.error(GlobalErrorCodeConstants.NOT_FOUND.getCode(),
                GlobalErrorCodeConstants.NOT_FOUND.getMsg());
    }

    /**
     * 兜底异常处理
     * 修复 BUG-008：原实现将 e.getMessage() 原样回显，导致**完整 SQL 语句、表结构与字段名、
     * 包路径与类名**泄露给客户端（实测：`### SQL: insert into faq(...) values(?,?,?)`）。
     * 现改为：服务端保留完整堆栈日志，客户端仅收到统一文案。
     */
    @ExceptionHandler(value = Exception.class)
    public CommonResult<?> exception(Exception e) {
        //打印错误日志（含堆栈，仅服务端可见）
        logger.error("全局异常",e);
        //返回统一失败结果，不回显任何内部细节
        return CommonResult.error(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getMsg());
    }
}
