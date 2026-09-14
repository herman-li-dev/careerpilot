package com.hermanli.careerpilot.identity;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Tag("external")
@Transactional
class RegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserAccountService userAccountService;

    @Test
    void registersNormalizedEmailAndStoresOnlyBcryptHash() throws Exception {
        String emailPrefix = "i02-" + UUID.randomUUID();
        String normalizedEmail = emailPrefix + "@example.com";
        String suppliedPassword = UUID.randomUUID().toString();

        String responseBody = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "  %s@Example.COM  ",
                                  "password": "%s"
                                }
                                """.formatted(emailPrefix, suppliedPassword)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(normalizedEmail))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String storedHash = jdbcTemplate.queryForObject(
                "select password_hash from app_user where email = ?",
                String.class,
                normalizedEmail
        );

        assertNotEquals(suppliedPassword, storedHash);
        assertTrue(storedHash.startsWith("$2"));
        assertTrue(userAccountService.passwordMatches(normalizedEmail, suppliedPassword));
        assertFalse(userAccountService.passwordMatches(normalizedEmail, UUID.randomUUID().toString()));
        assertFalse(responseBody.contains(suppliedPassword));
        assertFalse(responseBody.contains("passwordHash"));
    }

    @Test
    void rejectsDuplicateNormalizedEmail() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        String suppliedPassword = UUID.randomUUID().toString();
        String requestBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, suppliedPassword);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
    }
}
