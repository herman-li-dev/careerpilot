package com.hermanli.careerpilot.identity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

import java.util.OptionalLong;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTRIBUTE = "careerpilotCurrentUserId";

    private final SessionTokenService sessionTokenService;

    public AuthenticationInterceptor(SessionTokenService sessionTokenService) {
        this.sessionTokenService = sessionTokenService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Cookie sessionCookie = WebUtils.getCookie(request, SessionCookieService.COOKIE_NAME);
        if (sessionCookie == null) {
            throw new AuthenticationRequiredException();
        }

        OptionalLong userId = sessionTokenService.decodeUserId(sessionCookie.getValue());
        if (userId.isEmpty()) {
            throw new AuthenticationRequiredException();
        }

        request.setAttribute(USER_ID_ATTRIBUTE, userId.getAsLong());
        return true;
    }
}
