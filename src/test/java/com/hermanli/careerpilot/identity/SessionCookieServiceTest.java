package com.hermanli.careerpilot.identity;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCookieServiceTest {

    @Test
    void localCookieIsHttpOnlyStrictAndNotSecure() {
        SessionCookieService service = new SessionCookieService(false, Duration.ofHours(8));

        ResponseCookie cookie = service.create(UUID.randomUUID().toString());

        assertTrue(cookie.isHttpOnly());
        assertFalse(cookie.isSecure());
        assertEquals("Strict", cookie.getSameSite());
        assertEquals("/api", cookie.getPath());
        assertEquals(Duration.ofHours(8), cookie.getMaxAge());
    }

    @Test
    void productionCookieAddsSecureAndLogoutPreservesFlags() {
        SessionCookieService service = new SessionCookieService(true, Duration.ofHours(8));

        ResponseCookie cookie = service.create(UUID.randomUUID().toString());
        ResponseCookie clearedCookie = service.clear();

        assertTrue(cookie.isSecure());
        assertTrue(clearedCookie.isSecure());
        assertTrue(clearedCookie.isHttpOnly());
        assertEquals("Strict", clearedCookie.getSameSite());
        assertEquals(Duration.ZERO, clearedCookie.getMaxAge());
    }
}
