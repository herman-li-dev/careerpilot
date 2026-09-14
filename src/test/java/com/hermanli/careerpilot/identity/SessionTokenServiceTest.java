package com.hermanli.careerpilot.identity;

import com.hermanli.careerpilot.config.SessionTokenConfig;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTokenServiceTest {

    @Test
    void roundTripsSignedUserId() throws Exception {
        SessionTokenService service = newService(Duration.ofHours(8));

        assertEquals(42L, service.decodeUserId(service.issue(42L)).orElseThrow());
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() throws Exception {
        SessionTokenService issuer = newService(Duration.ofHours(8));
        SessionTokenService verifier = newService(Duration.ofHours(8));

        assertTrue(verifier.decodeUserId(issuer.issue(42L)).isEmpty());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        SessionTokenService service = newService(Duration.ofSeconds(1));
        String token = service.issue(42L);

        Thread.sleep(1500);

        assertTrue(service.decodeUserId(token).isEmpty());
    }

    @Test
    void productionSecureCookieRequiresConfiguredSigningSecret() {
        SessionTokenConfig config = new SessionTokenConfig();

        assertThrows(IllegalStateException.class, () -> config.sessionSigningKey("", true));
        assertThrows(IllegalStateException.class, () -> config.sessionSigningKey("too-short", false));
    }

    private SessionTokenService newService(Duration duration) throws Exception {
        SessionTokenConfig config = new SessionTokenConfig();
        SecretKey key = config.sessionSigningKey("", false);
        return new SessionTokenService(
                config.jwtEncoder(key),
                config.jwtDecoder(key),
                Clock.systemUTC(),
                duration
        );
    }
}
