package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.CareerPilotExceptionHandler;
import com.hermanli.careerpilot.demo.careerpilot.DemoModeService;
import com.hermanli.careerpilot.identity.CurrentUser;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoAuthController.class)
@Import(CareerPilotExceptionHandler.class)
@TestPropertySource(properties = "careerpilot.demo.enabled=true")
class DemoAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoModeService demoModeService;

    @MockitoBean
    private SessionTokenService sessionTokenService;

    @MockitoBean
    private SessionCookieService sessionCookieService;

    @Test
    void createsSessionForServerOwnedSyntheticAccountWithoutCredentials() throws Exception {
        CurrentUser user = new CurrentUser(
                91L,
                DemoModeService.DEMO_EMAIL,
                "ACTIVE",
                Instant.parse("2026-09-02T10:00:00Z"),
                null
        );
        String token = "test-token-not-returned";
        ResponseCookie cookie = ResponseCookie.from(SessionCookieService.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api")
                .build();
        when(demoModeService.currentUser()).thenReturn(user);
        when(sessionTokenService.issue(user.id())).thenReturn(token);
        when(sessionCookieService.create(token)).thenReturn(cookie);

        mockMvc.perform(post("/auth/demo-login"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(jsonPath("$.data.id").value(91))
                .andExpect(jsonPath("$.data.email").value(DemoModeService.DEMO_EMAIL))
                .andExpect(content().string(not(containsString(token))));
    }
}
