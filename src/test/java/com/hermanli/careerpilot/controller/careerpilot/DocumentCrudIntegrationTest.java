package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.identity.RegisteredUser;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import com.hermanli.careerpilot.identity.UserAccountService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Tag("external")
@Transactional
class DocumentCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private SessionTokenService sessionTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsPastedDocumentsWithoutStartingParsing() throws Exception {
        Account account = createAccount();
        Cookie sessionCookie = sessionCookie(account.id());

        mockMvc.perform(post("/resumes")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Backend Co-op Resume",
                                  "rawText": "Synthetic resume text only."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Backend Co-op Resume"))
                .andExpect(jsonPath("$.data.rawText").value("Synthetic resume text only."))
                .andExpect(jsonPath("$.data.parseStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.parseError").isEmpty());

        mockMvc.perform(post("/job-descriptions")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Example Company — Backend Co-op",
                                  "rawText": "Synthetic job description text only."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Example Company — Backend Co-op"))
                .andExpect(jsonPath("$.data.rawText").value("Synthetic job description text only."))
                .andExpect(jsonPath("$.data.companyName").isEmpty())
                .andExpect(jsonPath("$.data.roleTitle").isEmpty())
                .andExpect(jsonPath("$.data.parseStatus").value("NOT_STARTED"));
    }

    @Test
    void listsOwnedDocumentsNewestFirstAndReadsOneOwnedRecord() throws Exception {
        Account account = createAccount();
        Cookie sessionCookie = sessionCookie(account.id());
        long olderResumeId = insertResume(account.id(), "Older resume", Instant.parse("2026-08-31T12:00:00Z"));
        long newerResumeId = insertResume(account.id(), "Newer resume", Instant.parse("2026-08-31T12:01:00Z"));
        long olderJobDescriptionId = insertJobDescription(
                account.id(),
                "Older job description",
                Instant.parse("2026-08-31T12:00:00Z")
        );
        long newerJobDescriptionId = insertJobDescription(
                account.id(),
                "Newer job description",
                Instant.parse("2026-08-31T12:01:00Z")
        );

        mockMvc.perform(get("/resumes").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(newerResumeId))
                .andExpect(jsonPath("$.data[1].id").value(olderResumeId));
        mockMvc.perform(get("/resumes/{resumeId}", olderResumeId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Older resume"));

        mockMvc.perform(get("/job-descriptions").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(newerJobDescriptionId))
                .andExpect(jsonPath("$.data[1].id").value(olderJobDescriptionId));
        mockMvc.perform(get("/job-descriptions/{jobDescriptionId}", olderJobDescriptionId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Older job description"));
    }

    @Test
    void validatesBlankPastedTextAndRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/resumes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/job-descriptions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

        Cookie sessionCookie = sessionCookie(createAccount().id());
        mockMvc.perform(post("/resumes")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Resume\",\"rawText\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.rawText").value("Resume text is required."));
        mockMvc.perform(post("/job-descriptions")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Role\",\"rawText\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.rawText").value("Job description text is required."));
    }

    @Test
    void crossUserReadsAndDeletesReturnNotFoundWithoutChangingTheRecord() throws Exception {
        Account owner = createAccount();
        Account otherUser = createAccount();
        long resumeId = insertResume(owner.id(), "Owner resume", Instant.parse("2026-08-31T12:00:00Z"));
        long jobDescriptionId = insertJobDescription(
                owner.id(),
                "Owner job description",
                Instant.parse("2026-08-31T12:00:00Z")
        );
        Cookie otherCookie = sessionCookie(otherUser.id());

        mockMvc.perform(get("/resumes/{resumeId}", resumeId).cookie(otherCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete("/resumes/{resumeId}", resumeId).cookie(otherCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertEquals(1, documentCount("resume", resumeId));

        mockMvc.perform(get("/job-descriptions/{jobDescriptionId}", jobDescriptionId).cookie(otherCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete("/job-descriptions/{jobDescriptionId}", jobDescriptionId).cookie(otherCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertEquals(1, documentCount("job_description", jobDescriptionId));
    }

    @Test
    void ownerCanDeleteDocuments() throws Exception {
        Account account = createAccount();
        Cookie sessionCookie = sessionCookie(account.id());
        long resumeId = insertResume(account.id(), "Disposable resume", Instant.parse("2026-08-31T12:00:00Z"));
        long jobDescriptionId = insertJobDescription(
                account.id(),
                "Disposable job description",
                Instant.parse("2026-08-31T12:00:00Z")
        );

        mockMvc.perform(delete("/resumes/{resumeId}", resumeId).cookie(sessionCookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/job-descriptions/{jobDescriptionId}", jobDescriptionId).cookie(sessionCookie))
                .andExpect(status().isNoContent());

        assertEquals(0, documentCount("resume", resumeId));
        assertEquals(0, documentCount("job_description", jobDescriptionId));
    }

    private Account createAccount() {
        RegisteredUser user = userAccountService.register(
                "d02-" + UUID.randomUUID() + "@example.com",
                UUID.randomUUID().toString()
        );
        return new Account(user.id());
    }

    private Cookie sessionCookie(long userId) {
        Cookie cookie = new Cookie(SessionCookieService.COOKIE_NAME, sessionTokenService.issue(userId));
        cookie.setHttpOnly(true);
        cookie.setPath("/api");
        return cookie;
    }

    private long insertResume(long userId, String title, Instant createdAt) {
        return jdbcTemplate.queryForObject(
                """
                insert into resume (user_id, title, raw_text, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                returning id
                """,
                Long.class,
                userId,
                title,
                "Synthetic resume text.",
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private long insertJobDescription(long userId, String title, Instant createdAt) {
        return jdbcTemplate.queryForObject(
                """
                insert into job_description (user_id, title, raw_text, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                returning id
                """,
                Long.class,
                userId,
                title,
                "Synthetic job description text.",
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private int documentCount(String tableName, long documentId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where id = ?",
                Integer.class,
                documentId
        );
    }

    private record Account(long id) {
    }
}
