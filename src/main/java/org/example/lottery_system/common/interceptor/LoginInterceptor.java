package org.example.lottery_system.common.interceptor;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.lottery_system.common.errorcode.GlobalErrorCodeConstants;
import org.example.lottery_system.common.utils.JWTUtil;
import org.example.lottery_system.service.enums.UserIdentityEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

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
                writeJson(response, GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                        GlobalErrorCodeConstants.UNAUTHORIZED.getMsg());
                return false;
            }
            // 少数管理接口：除登录外，还要求 JWT 里 identity=ADMIN（路径与 admin 后台调用一致即可）
            String path = stripContextPath(request);
            boolean adminOnly = "/activity/create".equals(path)
                    || "/activity/find-list".equals(path)
                    || path.startsWith("/prize/")
                    || "/user/base-user/find-list".equals(path);
            Object identity = claims.get("identity");
            boolean isAdmin = identity != null
                    && UserIdentityEnum.ADMIN.name().equalsIgnoreCase(identity.toString());
            if (adminOnly && !isAdmin) {
                writeJson(response, GlobalErrorCodeConstants.FORBIDDEN.getCode(),
                        GlobalErrorCodeConstants.FORBIDDEN.getMsg());
                return false;
            }
        } catch (Exception e) {
            log.error("token解析异常: {}", e.getMessage());
            writeJson(response, GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "Token解析异常");
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

    private static void writeJson(HttpServletResponse response, int code, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        response.getWriter().write(String.format("{\"code\":%d,\"message\":\"%s\"}", code, message));
    }
}
