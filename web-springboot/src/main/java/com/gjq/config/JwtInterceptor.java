package com.gjq.config;

import com.gjq.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Value("${app.security.internal-service-token:}")
    private String internalServiceToken;

    @Value("${app.security.internal-header-name:X-Internal-Service-Token}")
    private String internalHeaderName;

    public JwtInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (isInternalRequest(request)) {
            if (isTrustedInternalRequest(request)) {
                request.setAttribute("internalService", true);
                return true;
            }
            writeUnauthorized(response, "内部服务鉴权失败");
            return false;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            if (jwtUtil.validateToken(token)) {
                request.setAttribute("userId", jwtUtil.getUserIdFromToken(token));
                request.setAttribute("username", jwtUtil.getUsernameFromToken(token));
                return true;
            }
        }

        writeUnauthorized(response, "未授权，请先登录");
        return false;
    }

    private boolean isInternalRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri != null && requestUri.contains("/internal/");
    }

    private boolean isTrustedInternalRequest(HttpServletRequest request) {
        if (!StringUtils.hasText(internalServiceToken)) {
            return false;
        }
        if (!isInternalRequest(request)) {
            return false;
        }
        String token = request.getHeader(internalHeaderName);
        return internalServiceToken.equals(token);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }
}
