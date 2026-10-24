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
import java.util.Set;

@RestControllerAdvice // 全局异常处理类
public class GlobalExceptionHandler {
    private static final Logger logger= LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 预期内的业务拒绝错误码（用户输入不合法 / 违反业务规则）。
     *
     * <p>修复 BUG-011：{@link ServiceException} 原先**一律**以 ERROR 级别打印完整堆栈，
     * 导致「邮箱已注册」这类可预期的业务拒绝也产生 ERROR + 50 行堆栈。
     * 实测证据：2026-09-14 13:35:20 / 13:36:41 应用日志中，
     * 客户端重复注册（Selenium 用例的账号预置步骤）触发了两次
     * {@code ERROR GlobalExceptionHandler - 服务异常 ... ServiceException: 邮箱已注册}。
     *
     * <p>这些错误码属于「客户端可自行纠正」的拒绝，服务端无故障：
     * 100~112 人员/短信模块校验失败、113 需管理员权限、114~118 短信与身份校验、
     * 200~206 活动参数、300/301 奖品参数、400~403 抽奖规则、500 图片参数。
     * 命中白名单 → WARN 级别，仅记录 code/message，**不打印堆栈**；
     * 未命中 → 视为服务端真实故障，保持 ERROR + 完整堆栈（便于定位）。
     *
     * <p>注意：此处不能用「错误码 &lt; 500 即业务错误」来判定 —— 全局错误码 400/401/403/415
     * 是 HTTP 语义错误码，与业务错误码数值区间重叠，按区间判定会误判。
     */
    private static final Set<Integer> EXPECTED_BUSINESS_ERROR_CODES = Set.of(
            // 人员模块：输入校验失败
            100, // 注册失败（入参为空）
            101, // 邮箱校验失败
            102, // 手机号校验失败
            103, // 身份校验失败
            104, // 密码为空
            105, // 密码格式错误
            106, // 邮箱已注册
            107, // 手机号已注册
            // 登录 / 用户信息
            108, // 登录信息不存在
            109, // 登录不存在
            110, // 用户信息为空
            111, // 密码校验失败
            112, // 验证码校验失败
            113, // 仅管理员可在后台代建普通用户
            114, // 该手机号未登记为管理员账号
            115, // 短信发送过于频繁
            116, // 短信发送次数已达上限
            117, // 短信发送失败
            118, // 该手机号尚未注册
            // 活动模块：业务规则不满足
            200, 201, 202, 203, 204, 205, 206,
            // 奖品模块
            300,
            // 抽奖模块
            400, // 抽奖活动或相关奖品不存在
            401, // 抽奖活动已结束
            402, // 活动奖品已被抽取
            403, // 中奖人员与奖品数量不一致
            // 图片模块
            500  // 图片上传失败（文件名为空 / 落盘失败）
    );

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
        if (isExpectedBusinessRejection(e.getCode())) {
            // 可预期的业务拒绝：只需一条可检索的 WARN，不打印堆栈（修复 BUG-011）
            logger.warn("业务拒绝: code={} message={}", e.getCode(), e.getMessage());
        } else {
            // 服务端真实故障：保留完整堆栈便于定位
            logger.error("服务异常", e);
        }
        //返回失败结果
        return CommonResult.error(e.getCode(),e.getMessage());
    }

    /**
     * 判断是否为预期内的业务拒绝（客户端可自行纠正，服务端无故障）。
     * 见 {@link #EXPECTED_BUSINESS_ERROR_CODES}。
     */
    private static boolean isExpectedBusinessRejection(Integer code) {
        return code != null && EXPECTED_BUSINESS_ERROR_CODES.contains(code);
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
