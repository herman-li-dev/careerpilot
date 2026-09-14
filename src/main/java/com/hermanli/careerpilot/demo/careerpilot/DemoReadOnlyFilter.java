package com.hermanli.careerpilot.demo.careerpilot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "careerpilot.demo", name = "enabled", havingValue = "true")
public class DemoReadOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> ALLOWED_POST_PATHS = Set.of(
            "/auth/demo-login",
            "/auth/logout"
    );
    private static final Pattern RESUME_REVIEW = Pattern.compile("/resumes/[0-9]+/review");
    private static final Pattern INTERVIEW_PREP = Pattern.compile("/analyses/[0-9]+/interview-prep");
    private static final byte[] READ_ONLY_RESPONSE = """
            {"success":false,"data":null,"error":{"code":"DEMO_READ_ONLY","message":"The public demo is read-only."}}
            """.strip().getBytes(StandardCharsets.UTF_8);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = applicationPath(request);
        if (SAFE_METHODS.contains(request.getMethod()) || isAllowedPost(request.getMethod(), path)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setContentLength(READ_ONLY_RESPONSE.length);
        response.getOutputStream().write(READ_ONLY_RESPONSE);
    }

    private boolean isAllowedPost(String method, String path) {
        return "POST".equals(method)
                && (ALLOWED_POST_PATHS.contains(path)
                || RESUME_REVIEW.matcher(path).matches()
                || INTERVIEW_PREP.matcher(path).matches());
    }

    private String applicationPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
    }
}
