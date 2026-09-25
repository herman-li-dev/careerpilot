package com.hermanli.careerpilot.publicrag;

import com.hermanli.careerpilot.api.ApiResponse;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.identity.CurrentUserId;
import com.hermanli.careerpilot.identity.CurrentUserIdArgumentResolver;
import com.hermanli.careerpilot.identity.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClerkApplicationAuthenticationTest {

    private static final String ISSUER = "https://example.clerk.accounts.dev";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        UserAccountService userAccountService = mock(UserAccountService.class);
        when(jwtDecoder.decode("token-a")).thenReturn(jwt("token-a", "user_a"));
        when(jwtDecoder.decode("token-b")).thenReturn(jwt("token-b", "user_b"));
        when(userAccountService.resolveOrCreateClerkUser(ISSUER, "user_a")).thenReturn(101L);
        when(userAccountService.resolveOrCreateClerkUser(ISSUER, "user_b")).thenReturn(202L);

        mockMvc = MockMvcBuilders.standaloneSetup(new IdentityFixtureController())
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .addInterceptors(new PublicRagAuthenticationInterceptor(jwtDecoder, userAccountService))
                .build();
    }

    @Test
    void rejectsMissingBearerToken() throws Exception {
        Exception exception = assertThrows(Exception.class,
                () -> mockMvc.perform(get("/users/identity")));
        assertInstanceOf(
                com.hermanli.careerpilot.identity.AuthenticationRequiredException.class,
                exception.getCause()
        );
    }

    @Test
    void derivesInternalUserIdFromVerifiedClerkSubject() throws Exception {
        mockMvc.perform(get("/users/identity")
                        .header("Authorization", "Bearer token-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(101));

        mockMvc.perform(get("/users/identity")
                        .header("Authorization", "Bearer token-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(202));
    }

    @Test
    void crossUserResourceAccessDoesNotRevealOwnership() throws Exception {
        Exception exception = assertThrows(Exception.class,
                () -> mockMvc.perform(get("/users/identity/202")
                        .header("Authorization", "Bearer token-a")));
        assertInstanceOf(ResourceNotFoundException.class, exception.getCause());

        mockMvc.perform(get("/users/identity/202")
                        .header("Authorization", "Bearer token-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(202));
    }

    private Jwt jwt(String token, String subject) {
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(Instant.parse("2026-09-20T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-20T01:00:00Z"))
                .build();
    }

    @RestController
    @RequestMapping("/users/identity")
    static class IdentityFixtureController {

        @GetMapping
        ApiResponse<Long> currentUser(@CurrentUserId long userId) {
            return ApiResponse.success(userId);
        }

        @GetMapping("/{ownerId}")
        ApiResponse<Long> ownedResource(
                @PathVariable long ownerId,
                @CurrentUserId long userId
        ) {
            if (ownerId != userId) {
                throw new ResourceNotFoundException();
            }
            return ApiResponse.success(ownerId);
        }
    }
}
