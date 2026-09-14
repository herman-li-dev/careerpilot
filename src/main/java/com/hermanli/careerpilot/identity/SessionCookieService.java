package com.hermanli.careerpilot.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SessionCookieService {

    public static final String COOKIE_NAME = "careerpilot_session";
    public static final String COOKIE_PATH = "/api";

    private final boolean secure;
    private final Duration sessionDuration;

    public SessionCookieService(
            @Value("${careerpilot.auth.cookie-secure}") boolean secure,
            @Value("${careerpilot.auth.session-duration}") Duration sessionDuration
    ) {
        this.secure = secure;
        this.sessionDuration = sessionDuration;
    }

    public ResponseCookie create(String token) {
        return baseCookie(token)
                .maxAge(sessionDuration)
                .build();
    }

    public ResponseCookie clear() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}
