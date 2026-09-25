package com.hermanli.careerpilot.publicrag;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.hermanli.careerpilot.api.CareerPilotExceptionHandler;
import com.hermanli.careerpilot.controller.careerpilot.PublicRagSessionController;
import com.hermanli.careerpilot.identity.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicRagSessionControllerTest {

    private JwtDecoder jwtDecoder;
    private UserAccountService userAccountService;
    private MockMvc mockMvc;
    private PublicRagSecurityAuditInterceptor securityAuditInterceptor;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        userAccountService = mock(UserAccountService.class);
        securityAuditInterceptor = new PublicRagSecurityAuditInterceptor();
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicRagSessionController())
                .setControllerAdvice(new CareerPilotExceptionHandler())
                .addInterceptors(
                        securityAuditInterceptor,
                        new PublicRagAuthenticationInterceptor(jwtDecoder, userAccountService)
                )
                .build();
    }

    @Test
    void rejectsRequestsWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/rag/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rejectsInvalidBearerTokenWithoutExposingDecoderDetails() throws Exception {
        when(jwtDecoder.decode("invalid-token")).thenThrow(new JwtException("sensitive decoder detail"));

        mockMvc.perform(get("/rag/session").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message")
                        .value("Authentication is required."));
    }

    @Test
    void rejectsAmbiguousOrMalformedAuthorizationHeadersBeforeDecoding() throws Exception {
        mockMvc.perform(get("/rag/session").header("Authorization", "Basic credentials"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/rag/session").header(
                        "Authorization",
                        "Bearer first-token",
                        "Bearer second-token"
                ))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/rag/session").header("Authorization", "Bearer token with whitespace"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void auditLogAndResponseHeadersContainMetadataOnly() throws Exception {
        String tokenCanary = "private-token-canary";
        String decoderCanary = "private-decoder-canary";
        String pathCanary = "private-path-canary";
        when(jwtDecoder.decode(tokenCanary)).thenThrow(new JwtException(decoderCanary));
        Logger logger = (Logger) LoggerFactory.getLogger(PublicRagSecurityAuditInterceptor.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String response = mockMvc.perform(get("/rag/session")
                            .header("Authorization", "Bearer " + tokenCanary))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("Pragma", "no-cache"))
                    .andExpect(header().string(
                            PublicRagSecurityAuditInterceptor.REQUEST_ID_HEADER,
                            not(blankOrNullString())
                    ))
                    .andReturn().getResponse().getContentAsString();
            MockHttpServletRequest unknownRequest = new MockHttpServletRequest(
                    "GET",
                    "/rag/" + pathCanary
            );
            MockHttpServletResponse unknownResponse = new MockHttpServletResponse();
            securityAuditInterceptor.preHandle(unknownRequest, unknownResponse, new Object());
            unknownResponse.setStatus(404);
            securityAuditInterceptor.afterCompletion(unknownRequest, unknownResponse, new Object(), null);

            String auditMessages = appender.list.stream()
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(auditMessages)
                    .contains(
                            "public_rag_request",
                            "method=GET",
                            "route=session",
                            "route=other",
                            "status=401"
                    )
                    .doesNotContain(tokenCanary, decoderCanary, pathCanary);
            assertThat(response).doesNotContain(tokenCanary, decoderCanary, pathCanary);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void acceptsValidClerkSessionWithoutReturningIdentity() throws Exception {
        when(jwtDecoder.decode("valid-token")).thenReturn(Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .issuer("https://example.clerk.accounts.dev")
                .subject("user_123")
                .issuedAt(Instant.parse("2026-09-20T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-20T01:00:00Z"))
                .build());
        when(userAccountService.resolveOrCreateClerkUser(
                "https://example.clerk.accounts.dev",
                "user_123"
        )).thenReturn(42L);

        mockMvc.perform(get("/rag/session").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());
        verify(userAccountService).resolveOrCreateClerkUser(
                "https://example.clerk.accounts.dev",
                "user_123"
        );
    }

    @Test
    void allowsCorsPreflightWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/rag/session"))
                .andExpect(status().isOk());
    }
}
