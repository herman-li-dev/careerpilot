package com.hermanli.careerpilot.identity;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "careerpilot.auth.session-duration=PT1S"
})
@AutoConfigureMockMvc
@Tag("external")
@Transactional
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loginCurrentUserAndLogoutUseCookieSession() throws Exception {
        Credentials credentials = registerAccount();
        jdbcTemplate.update(
                """
                insert into user_profile (
                    user_id, target_role, target_location, work_authorization,
                    weekly_hours, education_summary
                ) values (?, ?, ?, ?, ?, ?)
                """,
                credentials.userId(),
                "Backend Developer Co-op",
                "Vancouver, BC",
                "Synthetic authorization summary",
                10,
                "Synthetic education summary"
        );

        MockHttpServletResponse loginResponse = login(credentials)
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api")))
                .andExpect(header().string("Set-Cookie", not(containsString("Secure"))))
                .andExpect(jsonPath("$.data.id").value(credentials.userId()))
                .andExpect(jsonPath("$.data.email").value(credentials.email()))
                .andExpect(jsonPath("$.data.profile.targetRole").value("Backend Developer Co-op"))
                .andExpect(content().string(not(containsString(credentials.password()))))
                .andReturn()
                .getResponse();

        Cookie sessionCookie = loginResponse.getCookie(SessionCookieService.COOKIE_NAME);
        assertNotNull(sessionCookie);

        mockMvc.perform(get("/users/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(credentials.userId()))
                .andExpect(jsonPath("$.data.profile.weeklyHours").value(10));

        MockHttpServletResponse logoutResponse = mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andReturn()
                .getResponse();
        assertEquals(0, logoutResponse.getCookie(SessionCookieService.COOKIE_NAME).getMaxAge());
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnTheSameSafeError() throws Exception {
        Credentials credentials = registerAccount();
        String wrongPassword = UUID.randomUUID().toString();

        String wrongPasswordResponse = login(credentials.email(), wrongPassword)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String unknownEmailResponse = login(
                "missing-" + UUID.randomUUID() + "@example.com",
                wrongPassword
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(wrongPasswordResponse, unknownEmailResponse);
    }

    @Test
    void currentUserRejectsMissingAndInvalidSignatureCookies() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/users/me").cookie(new Cookie(
                        SessionCookieService.COOKIE_NAME,
                        UUID.randomUUID().toString()
                )))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void currentUserRejectsExpiredCookie() throws Exception {
        Credentials credentials = registerAccount();
        Cookie sessionCookie = login(credentials)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(SessionCookieService.COOKIE_NAME);
        assertNotNull(sessionCookie);

        Thread.sleep(1500);

        mockMvc.perform(get("/users/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    private Credentials registerAccount() {
        String email = "i03-" + UUID.randomUUID() + "@example.com";
        String password = UUID.randomUUID().toString();
        RegisteredUser user = userAccountService.register(email, password);
        return new Credentials(user.id(), email, password);
    }

    private org.springframework.test.web.servlet.ResultActions login(Credentials credentials) throws Exception {
        return login(credentials.email(), credentials.password());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(email, password)));
    }

    private record Credentials(long userId, String email, String password) {
    }
}
