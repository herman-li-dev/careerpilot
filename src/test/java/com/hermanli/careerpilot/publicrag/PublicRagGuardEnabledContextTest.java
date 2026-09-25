package com.hermanli.careerpilot.publicrag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "careerpilot.public-rag.auth.enabled=true",
        "careerpilot.public-rag.auth.issuer=https://example.clerk.accounts.dev",
        "careerpilot.public-rag.auth.authorized-parties=http://localhost:3000",
        "careerpilot.public-rag.guard.enabled=true",
        "careerpilot.public-rag.guard.identity-hmac-secret=synthetic-guard-secret-at-least-32-bytes"
})
class PublicRagGuardEnabledContextTest {

    @Autowired
    private PublicRagGuardService guardService;

    @Test
    void startsOnlyWithExplicitAuthenticationAndGuardConfiguration() {
        assertThat(guardService).isNotNull();
    }
}
