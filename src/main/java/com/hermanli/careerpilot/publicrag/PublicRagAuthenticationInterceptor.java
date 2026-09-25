package com.hermanli.careerpilot.publicrag;

import com.hermanli.careerpilot.identity.AuthenticationInterceptor;
import com.hermanli.careerpilot.identity.AuthenticationRequiredException;
import com.hermanli.careerpilot.identity.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;
import java.util.List;

final class PublicRagAuthenticationInterceptor implements HandlerInterceptor {

    static final String CLERK_ISSUER_ATTRIBUTE = "careerpilotClerkIssuer";
    static final String CLERK_SUBJECT_ATTRIBUTE = "careerpilotClerkSubject";

    private final JwtDecoder jwtDecoder;
    private final UserAccountService userAccountService;

    PublicRagAuthenticationInterceptor(JwtDecoder jwtDecoder, UserAccountService userAccountService) {
        this.jwtDecoder = jwtDecoder;
        this.userAccountService = userAccountService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        List<String> authorizationHeaders = Collections.list(request.getHeaders("Authorization"));
        if (authorizationHeaders.size() != 1) {
            throw new AuthenticationRequiredException();
        }

        String header = authorizationHeaders.getFirst();
        if (header == null || header.length() <= 7 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new AuthenticationRequiredException();
        }
        String token = header.substring(7).trim();
        if (token.isEmpty() || token.chars().anyMatch(Character::isWhitespace)) {
            throw new AuthenticationRequiredException();
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
            String subject = jwt.getSubject();
            long userId = userAccountService.resolveOrCreateClerkUser(issuer, subject);
            request.setAttribute(AuthenticationInterceptor.USER_ID_ATTRIBUTE, userId);
            request.setAttribute(CLERK_ISSUER_ATTRIBUTE, issuer);
            request.setAttribute(CLERK_SUBJECT_ATTRIBUTE, subject);
            return true;
        } catch (JwtException exception) {
            throw new AuthenticationRequiredException();
        }
    }
}
