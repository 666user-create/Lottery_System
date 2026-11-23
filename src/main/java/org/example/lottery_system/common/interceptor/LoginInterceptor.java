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
             * 为什么每请求都回库：JWT 无服务端状态，claim 只是登录那一刻的快照。
             * 只在登录时校验的话，账号被停用 / 被降级后，已签发的旧 token 仍能一直用到过期
             * ——"停用""降级"就形同虚设。因此每次请求都要回库确认。
             *
             * 代价：每个带 token 的请求多一次主键查询（微秒级，且全库无外键、数据量小）。
             * 收益不止状态校验：本方法后面判管理员时也要 identity，同一次查询零成本带出；
             * 令牌代数（token_version）同样搭这趟车，不额外增加查询。
             */
            Long userId = JWTUtil.getUserId(claims);   // 统一走工具方法：Number→longValue，杜绝窄化溢出
            UserDO currentUser = null;
            if (userId != null) {
                currentUser = userMapper.selectById(userId);
                if (currentUser == null) {
                    writeJson(request, response, GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                            GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
                    return false;
                }
                /*
                 * 状态校验放在代数校验之前：两者都会拒绝，但给用户的提示应尽可能具体。
                 * 账号被停用时，用户最需要知道的是"我被停用了"（能去联系管理员），
                 * 而不是笼统的"登录状态已失效"（用户只会以为是登录过期，反复重登）。
                 * 因此先判状态，命中就用「账号已停用」的具体文案。
                 */
                if (!UserStatusEnum.isActive(currentUser.getStatus())) {
                    log.info("已停用账号访问被拦截: userId={}, url={}", userId, request.getRequestURL());
                    writeJson(request, response, GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                            ServiceErrorCodeConstants.USER_DISABLED.getMsg());
                    return false;
                }
                /*
                 * 令牌代数校验：改密 / 停用 / 降级时 user.token_version 会 +1，
                 * 因此"签发时的代数"与"库中当前代数"不一致，就说明这张票已被主动作废。
                 *
                 * 放在状态校验之后：走到这里说明账号本身是启用的，
                 * 那么代数不符就只剩"改过密码"或"曾停用后又被启用"两种情况，
                 * 统一提示"登录状态已失效，请重新登录"是准确的——
                 * 用户按提示重新登录即可，不会被误导为账号有问题。
                 *
                 * 相比旧方案（Redis 记吊销时刻 + 比对 token 的 iat 秒级时间戳）：
                 *   1) 精确相等比较，没有"同秒签发逃过吊销"的精度漏洞；
                 *   2) 不引入 Redis 依赖，也就不用为 Redis 故障设计 fail-open/fail-close；
                 *   3) 版本号搭上面这次 selectById 顺路取回，零额外查询——反而比旧方案少一次 Redis 往返；
                 *   4) 改密、停用、降级共用同一机制，不必各写一套判断。
                 *
                 * 兼容性：版本号上线前签发的老 token 没有 ver claim，
                 * JWTUtil.getTokenVersion 按 0 处理，而 user.token_version 列默认值也是 0，
                 * 因此上线瞬间不会把在途会话全部踢掉。
                 */
                Integer tokenVer = JWTUtil.getTokenVersion(claims);
                int dbVer = currentUser.getTokenVersion() == null ? 0 : currentUser.getTokenVersion();
                if (tokenVer != null && tokenVer != dbVer) {
                    log.info("令牌代数已失效: userId={}, tokenVer={}, dbVer={}, url={}",
                            userId, tokenVer, dbVer, request.getRequestURL());
                    writeJson(request, response, GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                            "登录状态已失效，请重新登录");
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
