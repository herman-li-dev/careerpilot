package com.hermanli.careerpilot.publicrag;

import com.hermanli.careerpilot.identity.SessionTokenService;
import com.hermanli.careerpilot.controller.careerpilot.PublicRagResumeUploadController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "careerpilot.public-rag.auth.enabled=true",
        "careerpilot.public-rag.auth.issuer=https://example.clerk.accounts.dev",
        "careerpilot.public-rag.auth.authorized-parties=http://localhost:3000"
})
class PublicRagAuthEnabledContextTest {

    @Autowired
    private SessionTokenService sessionTokenService;

    @Autowired
    @Qualifier("publicRagClerkJwtDecoder")
    private JwtDecoder publicRagClerkJwtDecoder;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void existingAndClerkAuthenticationChainsStartTogether() {
        assertThat(sessionTokenService).isNotNull();
        assertThat(publicRagClerkJwtDecoder).isNotNull();
        assertThat(applicationContext.getBeansOfType(PublicRagResumeUploadController.class)).isEmpty();
    }
}
