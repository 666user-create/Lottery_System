package org.example.lottery_system.controller.handler;

import org.example.lottery_system.common.errorcode.GlobalErrorCodeConstants;
import org.example.lottery_system.common.exception.ControllerException;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.common.pojo.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(value = Exception.class)
    public CommonResult<?> exception(Exception e) {
        //打印错误日志
        logger.error("全局异常",e);
        //返回失败结果
        return CommonResult.error(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),e.getMessage());
    }
}
