package com.hermanli.careerpilot.publicrag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

final class PublicRagSecurityAuditInterceptor implements HandlerInterceptor {

    static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String START_NANOS_ATTRIBUTE =
            PublicRagSecurityAuditInterceptor.class.getName() + ".startNanos";
    private static final String REQUEST_ID_ATTRIBUTE =
            PublicRagSecurityAuditInterceptor.class.getName() + ".requestId";
    private static final Logger LOGGER = LoggerFactory.getLogger(PublicRagSecurityAuditInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        Object started = request.getAttribute(START_NANOS_ATTRIBUTE);
        long durationMillis = started instanceof Long startNanos
                ? Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L)
                : 0L;
        LOGGER.info(
                "public_rag_request requestId={} method={} route={} status={} durationMs={}",
                request.getAttribute(REQUEST_ID_ATTRIBUTE),
                request.getMethod(),
                routeLabel(request),
                response.getStatus(),
                durationMillis
        );
    }

    private String routeLabel(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath == null || contextPath.isEmpty()
                ? uri
                : uri.substring(Math.min(contextPath.length(), uri.length()));
        return switch (path) {
            case "/rag/session" -> "session";
            case "/rag/resume/validate" -> "resume_validate";
            case "/rag/resume/review" -> "resume_review";
            default -> "other";
        };
    }
}
