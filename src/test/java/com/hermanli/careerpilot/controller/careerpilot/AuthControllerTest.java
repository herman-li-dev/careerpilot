package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.CareerPilotExceptionHandler;
import com.hermanli.careerpilot.identity.CurrentUser;
import com.hermanli.careerpilot.identity.DuplicateEmailException;
import com.hermanli.careerpilot.identity.InvalidCredentialsException;
import com.hermanli.careerpilot.identity.RegisteredUser;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import com.hermanli.careerpilot.identity.UserAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(CareerPilotExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAccountService userAccountService;

    @MockitoBean
    private SessionTokenService sessionTokenService;

    @MockitoBean
    private SessionCookieService sessionCookieService;

    @Test
    void registersUserWithoutReturningPasswordData() throws Exception {
        String suppliedPassword = UUID.randomUUID().toString();
        when(userAccountService.register("candidate@example.com", suppliedPassword))
                .thenReturn(new RegisteredUser(
                        42L,
                        "candidate@example.com",
                        "ACTIVE",
                        Instant.parse("2026-08-30T20:00:00Z")
                ));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "candidate@example.com",
                                  "password": "%s"
                                }
                                """.formatted(suppliedPassword)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.email").value("candidate@example.com"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(content().string(not(containsString(suppliedPassword))))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void rejectsInvalidRegistrationFields() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.email").value("Email must be valid."))
                .andExpect(jsonPath("$.error.fieldErrors.password")
                        .value("Password must contain between 8 and 72 characters."));
    }

    @Test
    void returnsStableConflictForDuplicateEmail() throws Exception {
        String suppliedPassword = UUID.randomUUID().toString();
        when(userAccountService.register("candidate@example.com", suppliedPassword))
                .thenThrow(new DuplicateEmailException());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "candidate@example.com",
                                  "password": "%s"
                                }
                                """.formatted(suppliedPassword)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.error.message")
                        .value("An account already exists for this email."));
    }

    @Test
    void logsInWithHttpOnlySessionCookieAndPublicUserData() throws Exception {
        String suppliedPassword = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();
        RegisteredUser registeredUser = new RegisteredUser(
                42L,
                "candidate@example.com",
                "ACTIVE",
                Instant.parse("2026-08-30T20:00:00Z")
        );
        CurrentUser currentUser = new CurrentUser(
                42L,
                "candidate@example.com",
                "ACTIVE",
                registeredUser.createdAt(),
                null
        );
        ResponseCookie cookie = ResponseCookie.from(SessionCookieService.COOKIE_NAME, token)
                .httpOnly(true)
                .sameSite("Strict")
                .path("/api")
                .build();
        when(userAccountService.authenticate("candidate@example.com", suppliedPassword))
                .thenReturn(java.util.Optional.of(registeredUser));
        when(userAccountService.findCurrentUser(42L)).thenReturn(java.util.Optional.of(currentUser));
        when(sessionTokenService.issue(42L)).thenReturn(token);
        when(sessionCookieService.create(token)).thenReturn(cookie);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "candidate@example.com",
                                  "password": "%s"
                                }
                                """.formatted(suppliedPassword)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.profile").isEmpty())
                .andExpect(content().string(not(containsString(suppliedPassword))))
                .andExpect(content().string(not(containsString(token))));
    }

    @Test
    void returnsSameSafeErrorForInvalidCredentials() throws Exception {
        String suppliedPassword = UUID.randomUUID().toString();
        when(userAccountService.authenticate("candidate@example.com", suppliedPassword))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "candidate@example.com",
                                  "password": "%s"
                                }
                                """.formatted(suppliedPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("Email or password is incorrect."));
    }

    @Test
    void logoutClearsSessionCookieWithoutResponseBody() throws Exception {
        ResponseCookie clearedCookie = ResponseCookie.from(SessionCookieService.COOKIE_NAME, "")
                .httpOnly(true)
                .sameSite("Strict")
                .path("/api")
                .maxAge(0)
                .build();
        when(sessionCookieService.clear()).thenReturn(clearedCookie);

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(content().string(""));
    }
}
