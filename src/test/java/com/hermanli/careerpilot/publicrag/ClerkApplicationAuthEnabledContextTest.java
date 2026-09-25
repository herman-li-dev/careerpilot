package com.hermanli.careerpilot.publicrag;

import com.hermanli.careerpilot.controller.careerpilot.PublicRagLiveReviewController;
import com.hermanli.careerpilot.controller.careerpilot.ResumeReviewController;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "careerpilot.auth.clerk-application-enabled=true",
        "careerpilot.public-rag.auth.enabled=true",
        "careerpilot.public-rag.auth.issuer=https://example.clerk.accounts.dev",
        "careerpilot.public-rag.auth.authorized-parties=http://localhost:3000"
})
@AutoConfigureMockMvc
class ClerkApplicationAuthEnabledContextTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenService sessionTokenService;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void personalApiRejectsLegacyCookieWhenClerkApplicationAuthenticationIsEnabled() throws Exception {
        Cookie legacyCookie = new Cookie(
                SessionCookieService.COOKIE_NAME,
                sessionTokenService.issue(42L)
        );

        mockMvc.perform(get("/users/me").cookie(legacyCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void legacyLoginEndpointIsUnavailable() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"legacy@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void exposesOnlyTheGuardedReviewRouteInClerkApplicationMode() {
        org.assertj.core.api.Assertions.assertThat(applicationContext.getBeansOfType(ResumeReviewController.class))
                .isEmpty();
        org.assertj.core.api.Assertions.assertThat(applicationContext.getBeansOfType(PublicRagLiveReviewController.class))
                .hasSize(1);
    }
}
