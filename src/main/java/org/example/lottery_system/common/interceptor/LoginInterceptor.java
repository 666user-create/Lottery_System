package org.example.lottery_system.common.interceptor;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.lottery_system.common.errorcode.GlobalErrorCodeConstants;
import org.example.lottery_system.common.errorcode.ServiceErrorCodeConstants;
import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.dao.dataobject.UserDO;
import org.example.lottery_system.dao.mapper.UserMapper;
import org.example.lottery_system.service.enums.UserIdentityEnum;
import org.example.lottery_system.service.enums.UserStatusEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    /*
     * 验签通过后把 Claims 放进 request，供 OperationLogInterceptor 复用，避免重复验签。
     * */
    public static final String CLAIMS_ATTRIBUTE = LoginInterceptor.class.getName() + ".CLAIMS";

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private org.example.lottery_system.service.TokenRevocationService tokenRevocationService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = request.getHeader("token");
        log.info("url:{}", request.getRequestURL());
        try {
            Claims claims = JWTUtil.parseJWT(token);
            if (claims == null) {
                writeJson(request, response, GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                        GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
                return false;
            }
            // 供操作日志拦截器复用，避免同一请求重复验签
            request.setAttribute(CLAIMS_ATTRIBUTE, claims);

            /*
             * 停用校验：JWT 无服务端状态，只在登录时校验的话，
             * 账号被停用后已签发的旧 token 仍可一直用到过期——停用就形同虚设。
             * 因此每次请求都要回库确认状态。
             *
             * 代价：每个带 token 的请求多一次主键查询（微秒级，且全库无外键、数据量小）。
             * 若将来流量上来，可改为「停用时把该用户 id 写入 Redis 黑名单」，
             * 查缓存代替查库，或给 token 加版本号让旧 token 失效。
             */
            Long userId = JWTUtil.getUserId(claims);   // 统一走工具方法：Number→longValue，杜绝窄化溢出
            UserDO currentUser = null;
            if (userId != null) {
                /*
                 * 令牌吊销校验：改密前签发的旧 token 立即失效（不等自然过期）。
                 * 比较签发时间而非拉黑键，是为了不误伤改密后立刻重新登录拿到的新 token。
                 * Redis 不可用时 getRevokedBefore 返回 null → 按无吊销放行（fail-open）。
                 */
                Long revokedBefore = tokenRevocationService.getRevokedBeforeEpochSecond(userId);
                // 严格小于（不是 <=）：JWT 的 iat 只有秒级精度，同一秒内无法区分
                // "改密前签发"与"改密后重登"。取 < 的取舍：
                //   宁可漏——与改密同秒签发的旧 token 逃过吊销、活到自然过期（≤1h，
                //   等同于没做吊销时的基线，窗口从"任何 token 1h"缩到"仅同秒 token"）；
                //   不可误杀——用 <= 时，用户改密后同一秒内重新登录，新 token 会被
                //   自己的吊销记录踢掉，表现为"改完密码立刻被登出"，不可接受。
                // 人工操作下"login 与改密同秒"概率极低；要彻底消除需 token 加 jti+黑名单。
                if (revokedBefore != null && claims.getIssuedAt() != null
                        && claims.getIssuedAt().getTime() / 1000 < revokedBefore) {
                    log.info("改密后旧令牌已吊销: userId={}, url={}", userId, request.getRequestURL());
                    writeJson(request, response, GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                            "密码已修改，请重新登录");
                    return false;
                }
                currentUser = userMapper.selectById(userId);
                if (currentUser == null) {
                    writeJson(request, response, GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                            GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
                    return false;
                }
                if (!UserStatusEnum.isActive(currentUser.getStatus())) {
                    log.info("已停用账号访问被拦截: userId={}, url={}", userId, request.getRequestURL());
                    writeJson(request, response, GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                            ServiceErrorCodeConstants.USER_DISABLED.getMsg());
                    return false;
                }
            }

            // 管理接口：除登录外，还要求 JWT 里 identity=ADMIN（路径与 admin 后台调用一致即可）
            String path = stripContextPath(request);
            boolean adminOnly = "/activity/create".equals(path)
                    // 活动管理：结束活动 / 删除活动（仅管理端可用，用户端只读）
                    || "/activity/end".equals(path)
                    || "/activity/delete".equals(path)
                    || path.startsWith("/prize/")
                    || "/user/base-user/find-list".equals(path)
                    // 用户详情（除密码外全字段）：仅管理员
                    || "/user/base-user/find-detail".equals(path)
                    // 账号停用/启用：仅管理员
                    || "/user/status/update".equals(path)
                    // AI 客服管理端：FAQ 知识库增删查
                    || path.startsWith("/api/faq/")
                    // AI 客服管理端：工单列表查询与处理（用户端主动转人工 /api/handover/create 除外）
                    || "/api/handover/list".equals(path)
                    || "/api/handover/process".equals(path)
                    || "/api/handover/delete".equals(path)
                    // 中奖履约管理端：查看中奖记录并直接修改领奖状态（客户端 /api/winning/claim、/confirm 除外）
                    || path.startsWith("/api/winning/manage/")
                    // AI 客服：RAG 检索诊断（含知识库内容，仅管理员可见）
                    || path.startsWith("/api/ai/rag/")
                    // 操作日志：仅管理员可查看
                    || path.startsWith("/api/operation-log/")
                    // 抽奖执行：前端用 localStorage.user_identity 判断可被伪造，服务端必须兜底
                    || "/draw/prize".equals(path);
            /*
             * 管理接口要求 identity=ADMIN，以数据库为准，不用 JWT claim。
             * JWT 的 identity 是登录那一刻的快照：账号后来被降级，旧 token 仍带着 ADMIN
             * claim 一直到过期，权限就收不回来了。本次请求既然已经查过库取状态，
             * 顺带取 identity 是同一次查询的零成本收益。
             * 未取到用户（claims 里没有 id）一律不给管理员权限。
             */
            boolean isAdmin = currentUser != null
                    && UserIdentityEnum.ADMIN.name().equalsIgnoreCase(currentUser.getIdentity());
            if (adminOnly && !isAdmin) {
                writeJson(request, response, GlobalErrorCodeConstants.FORBIDDEN.getCode(),
                        GlobalErrorCodeConstants.FORBIDDEN.getMsg());
                return false;
            }
        } catch (Exception e) {
            log.error("token解析异常: {}", e.getMessage());
            writeJson(request, response, GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "Token解析异常");
            return false;
        }
        return true;
    }

    private static String stripContextPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (StringUtils.hasText(context) && path.startsWith(context)) {
            return path.substring(context.length());
        }
        return path;
    }

    /*
     * 手写 JSON 写响应（拦截器 preHandle=false 时框架不再走消息转换器）。
     *
     * 关键（2026-11-03 修复）：writeJson 直接 getWriter() 输出，绕过了
     * HttpMessageConverter → OperationLogResponseAdvice 没有触发机会 →
     * 审计日志的 result_code/result_msg 恒 null——恰恰是最该留业务码的 401/403。
     * 因此这里手动把业务码塞进与 Advice 相同的 request attribute「信箱」，
     * afterCompletion 的读取逻辑零改动即可拿到。
     * 注：本拦截器先于 LoginInterceptor 执行，但 afterCompletion 在外层拦截器
     *     最后才运行，attribute 此时写入完全来得及被读取。
     */
    private static void writeJson(HttpServletRequest request, HttpServletResponse response,
                                  int code, String message) throws Exception {
        request.setAttribute(OperationLogInterceptor.ATTR_RESULT_CODE, code);
        request.setAttribute(OperationLogInterceptor.ATTR_RESULT_MSG, message);
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        response.getWriter().write(String.format("{\"code\":%d,\"message\":\"%s\"}", code, message));
    }
}
