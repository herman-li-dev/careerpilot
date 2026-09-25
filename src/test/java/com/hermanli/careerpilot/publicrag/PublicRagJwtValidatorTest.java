package com.hermanli.careerpilot.publicrag;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PublicRagJwtValidatorTest {

    private final PublicRagJwtValidator validator = new PublicRagJwtValidator(
            Set.of("https://careerpilot.example.com")
    );

    @Test
    void acceptsSignedInSubjectFromAuthorizedFrontend() {
        assertThat(validator.validate(jwt("user_123", "https://careerpilot.example.com", null)).hasErrors())
                .isFalse();
    }

    @Test
    void rejectsMissingSubjectOrUnauthorizedFrontendAndAllowsMissingAuthorizedParty() {
        assertThat(validator.validate(jwt("", "https://careerpilot.example.com", null)).hasErrors()).isTrue();
        assertThat(validator.validate(jwt("user_123", "https://attacker.example", null)).hasErrors()).isTrue();
        assertThat(validator.validate(jwt("user_123", null, null)).hasErrors()).isFalse();
    }

    @Test
    void rejectsPendingSessions() {
        assertThat(validator.validate(jwt("user_123", "https://careerpilot.example.com", "pending")).hasErrors())
                .isTrue();
    }

    private Jwt jwt(String subject, String authorizedParty, String status) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuedAt(Instant.parse("2026-09-20T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-20T01:00:00Z"))
                .subject(subject);
        if (authorizedParty != null) {
            builder.claim("azp", authorizedParty);
        }
        if (status != null) {
            builder.claim("sts", status);
        }
        return builder.build();
    }
}
