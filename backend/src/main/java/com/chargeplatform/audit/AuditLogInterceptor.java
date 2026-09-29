package com.chargeplatform.audit;

import com.chargeplatform.audit.domain.AuditLog;
import com.chargeplatform.audit.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuditLogInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AuditLogInterceptor.class);
    private final AuditLogMapper auditLogs;

    public AuditLogInterceptor(AuditLogMapper auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        if (!isAuditable(request)) return;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) return;
        String role = authentication.getAuthorities().stream().findFirst().map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).orElse("");
        try {
            auditLogs.insert(new AuditLog(authentication.getName(), role, request.getMethod(), request.getRequestURI(), response.getStatus(), clientIp(request)));
        } catch (RuntimeException error) {
            log.warn("审计日志写入失败，不影响业务请求: method={}, path={}", request.getMethod(), request.getRequestURI(), error);
        }
    }

    private boolean isAuditable(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return path.startsWith(request.getContextPath() + "/api/")
                && !path.equals(request.getContextPath() + "/api/auth/login")
                && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
