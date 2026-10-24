package org.example.lottery_system.common.interceptor;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.dao.dataobject.OperationLogDO;
import org.example.lottery_system.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/*
* 操作日志拦截器（旁路采集）
*
* 设计要点：
* 1.只记「写操作」与登录，GET 查询一律不记，用于控制日志量；
* 2.不记录请求体与任何入参 —— 一是 /prize/create 是 multipart 带文件，
*   读 body 会把图片缓冲进内存；二是注册参数含 password/手机号/邮箱，
*   这些字段在库里是加密存储的，落明文等于绕过加密；
* 3.业务成功/失败由 OperationLogResponseAdvice 通过 request attribute 传递过来；
* 4.落库走专用线程池，队列满时丢弃日志，绝不阻塞请求线程；
* 5.本拦截器必须注册在 LoginInterceptor 之前，这样被 401/403 拒绝的请求也能留下记录。
* */
@Component
public class OperationLogInterceptor implements HandlerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(OperationLogInterceptor.class);

    //业务返回码与描述的 request attribute 名，供 OperationLogResponseAdvice 写入
    public static final String ATTR_RESULT_CODE = OperationLogInterceptor.class.getName() + ".RESULT_CODE";
    public static final String ATTR_RESULT_MSG = OperationLogInterceptor.class.getName() + ".RESULT_MSG";

    private static final String ATTR_START_TIME = OperationLogInterceptor.class.getName() + ".START_TIME";
    private static final String ATTR_USER_ID = OperationLogInterceptor.class.getName() + ".USER_ID";
    private static final String ATTR_IDENTITY = OperationLogInterceptor.class.getName() + ".IDENTITY";

    private static final String IDENTITY_ANONYMOUS = "ANONYMOUS";
    private static final int MAX_MSG_LENGTH = 500;

    //只记录这些方法，GET/HEAD/OPTIONS 不记
    private static final Set<String> LOGGED_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    //虽然是 POST 但语义是查询，明确排除
    private static final Set<String> EXCLUDED_PATHS = Set.of("/draw/winning-records/show");

    /*
     * 路径 -> {业务模块, 操作描述}
     * key 形如 "POST /activity/create"
     * 命中不了的请求会落到 DEFAULT_OPERATION，不会被静默丢弃
     * */
    private static final Map<String, String[]> OPERATION_MAPPING = new LinkedHashMap<>();
    private static final String[] DEFAULT_OPERATION = {"其它", "未登记操作"};

    static {
        //用户模块
        OPERATION_MAPPING.put("POST /user/register", new String[]{"用户模块", "注册用户"});
        OPERATION_MAPPING.put("POST /user/verification-code/send", new String[]{"用户模块", "发送短信验证码"});
        OPERATION_MAPPING.put("POST /user/password/login", new String[]{"用户模块", "密码登录"});
        OPERATION_MAPPING.put("POST /user/message/login", new String[]{"用户模块", "短信验证码登录"});
        //用户模块：账号安全（改邮箱/改密码属敏感操作，必须留审计痕迹）
        OPERATION_MAPPING.put("POST /user/security/verification-code/send", new String[]{"用户模块", "账号安全发送验证码"});
        OPERATION_MAPPING.put("POST /user/email/update", new String[]{"用户模块", "修改邮箱"});
        OPERATION_MAPPING.put("POST /user/password/update", new String[]{"用户模块", "修改密码"});
        //账号停用/启用属敏感操作，必须留审计痕迹
        OPERATION_MAPPING.put("POST /user/status/update", new String[]{"用户模块", "停用/启用账号"});
        //活动模块
        OPERATION_MAPPING.put("POST /activity/create", new String[]{"活动模块", "创建活动"});
        OPERATION_MAPPING.put("POST /activity/end", new String[]{"活动模块", "结束活动"});
        OPERATION_MAPPING.put("DELETE /activity/delete", new String[]{"活动模块", "删除活动"});
        //奖品模块
        OPERATION_MAPPING.put("POST /prize/create", new String[]{"奖品模块", "创建奖品"});
        OPERATION_MAPPING.put("DELETE /prize/delete", new String[]{"奖品模块", "删除奖品"});
        OPERATION_MAPPING.put("POST /prize/pic/upload", new String[]{"奖品模块", "上传奖品图片"});
        //抽奖模块
        OPERATION_MAPPING.put("POST /draw/prize", new String[]{"抽奖模块", "执行抽奖"});
        //客服模块：FAQ 知识库
        OPERATION_MAPPING.put("POST /api/faq/add", new String[]{"客服模块", "新增FAQ"});
        OPERATION_MAPPING.put("DELETE /api/faq/delete", new String[]{"客服模块", "删除FAQ"});
        //客服模块：转人工工单
        OPERATION_MAPPING.put("POST /api/handover/create", new String[]{"客服模块", "用户转人工"});
        OPERATION_MAPPING.put("POST /api/handover/process", new String[]{"客服模块", "处理转人工工单"});
        OPERATION_MAPPING.put("DELETE /api/handover/delete", new String[]{"客服模块", "删除工单"});
        //奖品发放：领奖 / 发货 / 收货(发货走 /api/handover/process,已在上方登记)
        OPERATION_MAPPING.put("POST /api/winning/claim", new String[]{"奖品发放", "申请领奖"});
        OPERATION_MAPPING.put("POST /api/winning/confirm", new String[]{"奖品发放", "确认收货"});
        OPERATION_MAPPING.put("POST /api/winning/manage/status", new String[]{"奖品发放", "管理端修改领奖状态"});
        OPERATION_MAPPING.put("POST /user/receiver-info", new String[]{"用户模块", "保存收货地址"});
        //客服模块：智能问答
        OPERATION_MAPPING.put("POST /api/chat", new String[]{"客服模块", "智能问答(流式)"});
        OPERATION_MAPPING.put("POST /api/chat/sync", new String[]{"客服模块", "智能问答(同步)"});
        OPERATION_MAPPING.put("POST /api/ai/chat", new String[]{"客服模块", "RAG智能客服(流式)"});
        OPERATION_MAPPING.put("POST /api/ai/chat/sync", new String[]{"客服模块", "RAG智能客服(同步)"});
    }

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    @Qualifier("operationLogExecutor")
    private ThreadPoolTaskExecutor operationLogExecutor;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        //本拦截器只做旁路采集，任何情况下都放行
        try {
            if (isLoggable(request, handler)) {
                request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
                fillUser(request);
            }
        } catch (Exception e) {
            logger.warn("操作日志预处理失败，已忽略: {}", e.getMessage());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            if (!isLoggable(request, handler)) {
                return;
            }
            OperationLogDO operationLogDO = buildOperationLog(request, response);
            submit(operationLogDO);
        } catch (Exception e) {
            logger.warn("操作日志采集失败，已忽略: {}", e.getMessage());
        }
    }

    private boolean isLoggable(HttpServletRequest request, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return false;
        }
        String method = request.getMethod();
        if (method == null || !LOGGED_METHODS.contains(method.toUpperCase())) {
            return false;
        }
        return !EXCLUDED_PATHS.contains(stripContextPath(request));
    }

    /*
     * 解析登录态。LoginInterceptor 已在解析成功后把 Claims 放进 request，
     * 命中时直接复用，避免重复验签；公开接口（如 /user/register）也可能带 token，做一次兜底解析。
     * */
    private void fillUser(HttpServletRequest request) {
        Claims claims = (Claims) request.getAttribute(LoginInterceptor.CLAIMS_ATTRIBUTE);
        if (claims == null) {
            claims = JWTUtil.parseJWT(request.getHeader("token"));
        }
        if (claims == null) {
            request.setAttribute(ATTR_IDENTITY, IDENTITY_ANONYMOUS);
            return;
        }
        Object id = claims.get("id");
        if (id != null) {
            try {
                request.setAttribute(ATTR_USER_ID, Long.valueOf(id.toString()));
            } catch (NumberFormatException ignored) {
                //id 不是数字时不记录用户，不影响其它字段
            }
        }
        Object identity = claims.get("identity");
        request.setAttribute(ATTR_IDENTITY,
                identity == null ? IDENTITY_ANONYMOUS : identity.toString());
    }

    private OperationLogDO buildOperationLog(HttpServletRequest request, HttpServletResponse response) {
        String method = request.getMethod();
        String path = stripContextPath(request);

        OperationLogDO operationLogDO = new OperationLogDO();
        operationLogDO.setUserId((Long) request.getAttribute(ATTR_USER_ID));
        Object identity = request.getAttribute(ATTR_IDENTITY);
        operationLogDO.setIdentity(identity == null ? IDENTITY_ANONYMOUS : identity.toString());

        String[] operation = OPERATION_MAPPING.getOrDefault(method + " " + path, DEFAULT_OPERATION);
        operationLogDO.setModule(operation[0]);
        operationLogDO.setOperation(operation[1]);
        operationLogDO.setHttpMethod(method);
        operationLogDO.setPath(path);
        operationLogDO.setResultCode((Integer) request.getAttribute(ATTR_RESULT_CODE));
        operationLogDO.setResultMsg(truncate((String) request.getAttribute(ATTR_RESULT_MSG)));
        operationLogDO.setHttpStatus(response.getStatus());

        Object startTime = request.getAttribute(ATTR_START_TIME);
        operationLogDO.setCostMs(startTime instanceof Long
                ? System.currentTimeMillis() - (Long) startTime : null);
        operationLogDO.setIp(clientIp(request));
        return operationLogDO;
    }

    /*
     * 提交到专用线程池。队列满时丢弃日志并告警，绝不把异常抛回请求线程。
     * */
    private void submit(OperationLogDO operationLogDO) {
        try {
            operationLogExecutor.execute(() -> operationLogService.save(operationLogDO));
        } catch (TaskRejectedException e) {
            logger.warn("操作日志队列已满，丢弃本条记录: {} {}",
                    operationLogDO.getHttpMethod(), operationLogDO.getPath());
        } catch (Exception e) {
            logger.warn("操作日志提交失败，已忽略: {}", e.getMessage());
        }
    }

    private static String stripContextPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (StringUtils.hasText(context) && path.startsWith(context)) {
            return path.substring(context.length());
        }
        return path;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_MSG_LENGTH ? text : text.substring(0, MAX_MSG_LENGTH);
    }
}
