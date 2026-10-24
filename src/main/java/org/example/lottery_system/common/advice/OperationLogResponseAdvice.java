package org.example.lottery_system.common.advice;

import jakarta.servlet.http.HttpServletRequest;
import org.example.lottery_system.common.interceptor.OperationLogInterceptor;
import org.example.lottery_system.common.pojo.CommonResult;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/*
这段代码在响应序列化之前瞥一眼 body：如果 body 是统一包装体 CommonResult，就把它的 code 和 message 两个字段 塞进 request attribute，供后面的 OperationLogInterceptor 写日志用；
其他类型（String、文件流等）直接跳过、原样放行，既不记录也不修改。(不影响SSE流式返回)
* */
@ControllerAdvice(basePackages = "org.example.lottery_system.controller")
public class OperationLogResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        //全部放行，实际过滤在 beforeBodyWrite 里按类型判断；返回 true 本身不改变任何响应行为
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        try {
            //只有统一包装体才有业务码；String / 文件流等一律跳过
            if (body instanceof CommonResult<?> commonResult
                    && request instanceof ServletServerHttpRequest servletServerHttpRequest) {
                HttpServletRequest httpServletRequest = servletServerHttpRequest.getServletRequest();
                httpServletRequest.setAttribute(OperationLogInterceptor.ATTR_RESULT_CODE,
                        commonResult.getCode());
                httpServletRequest.setAttribute(OperationLogInterceptor.ATTR_RESULT_MSG,
                        commonResult.getMessage());
            }
        } catch (Exception ignored) {
            //采集失败绝不能影响响应，静默忽略
        }
        return body;
    }
}
