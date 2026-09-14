package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.documents.DocumentParser;
import com.hermanli.careerpilot.identity.RegisteredUser;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import com.hermanli.careerpilot.identity.UserAccountService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Import(DocumentParsingIntegrationTest.FakeDocumentParserConfiguration.class)
@Tag("external")
@Transactional
class DocumentParsingIntegrationTest {

    private static final String VALID_RESUME_JSON = """
            {
              "skills": ["Java", "Spring Boot"],
              "education": ["Computer Science student"],
              "projects": ["Synthetic backend project"],
              "workExperience": [],
              "certifications": []
            }
            """;
    private static final String VALID_JOB_DESCRIPTION_JSON = """
            {
              "companyName": "Example Company",
              "roleTitle": "Backend Developer Co-op",
              "location": "Vancouver, BC",
              "responsibilities": ["Build backend APIs"],
              "requiredSkills": ["Java", "PostgreSQL"],
              "preferredSkills": ["Docker"],
              "experienceRequirements": ["Experience with software projects"]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private SessionTokenService sessionTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeDocumentParser fakeDocumentParser;

    @BeforeEach
    void resetParser() {
        fakeDocumentParser.resumeResponse = VALID_RESUME_JSON;
        fakeDocumentParser.jobDescriptionResponse = VALID_JOB_DESCRIPTION_JSON;
        fakeDocumentParser.failure = null;
    }

    @Test
    void validOutputStoresParsedJsonAndCompletesOwnedDocuments() throws Exception {
        Account account = createAccount();
        Cookie sessionCookie = sessionCookie(account.id());
        long resumeId = insertResume(account.id(), "Synthetic resume", "Original synthetic resume text.");
        long jobDescriptionId = insertJobDescription(
                account.id(),
                "Synthetic job description",
                "Original synthetic job description text."
        );

        mockMvc.perform(post("/resumes/{resumeId}/parse", resumeId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.parseError").isEmpty());
        mockMvc.perform(post("/job-descriptions/{jobDescriptionId}/parse", jobDescriptionId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.parseError").isEmpty());

        assertEquals("Original synthetic resume text.", rawText("resume", resumeId));
        assertEquals("Original synthetic job description text.", rawText("job_description", jobDescriptionId));
        assertEquals("COMPLETED", parseStatus("resume", resumeId));
        assertEquals("COMPLETED", parseStatus("job_description", jobDescriptionId));
        assertEquals("Java", parsedJsonField("resume", resumeId, "skills", 0));
        assertEquals("Example Company", parsedJsonField("job_description", jobDescriptionId, "companyName", null));
    }

    @Test
    void invalidOutputFailsWithoutReplacingTheOriginalTextAndCanBeRetried() throws Exception {
        Account account = createAccount();
        Cookie sessionCookie = sessionCookie(account.id());
        long resumeId = insertResume(account.id(), "Retry resume", "Original resume text remains unchanged.");
        fakeDocumentParser.resumeResponse = "{not valid json}";

        mockMvc.perform(post("/resumes/{resumeId}/parse", resumeId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.parseError")
                        .value("The parser returned invalid structured data. Please try again."));
        assertEquals("Original resume text remains unchanged.", rawText("resume", resumeId));
        assertNull(parsedJson("resume", resumeId));

        fakeDocumentParser.resumeResponse = VALID_RESUME_JSON;
        mockMvc.perform(post("/resumes/{resumeId}/parse", resumeId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("COMPLETED"));
        assertEquals("Java", parsedJsonField("resume", resumeId, "skills", 0));
    }

    @Test
    void providerFailureBecomesSafeFailedState() throws Exception {
        Account account = createAccount();
        Cookie sessionCookie = sessionCookie(account.id());
        long jobDescriptionId = insertJobDescription(account.id(), "Unavailable parser", "Original job description text.");
        fakeDocumentParser.failure = new IllegalStateException("provider details must not be exposed");

        mockMvc.perform(post("/job-descriptions/{jobDescriptionId}/parse", jobDescriptionId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.parseError")
                        .value("The document could not be parsed. Please try again."));
        assertEquals("Original job description text.", rawText("job_description", jobDescriptionId));
        assertNull(parsedJson("job_description", jobDescriptionId));
    }

    @Test
    void parsingRequiresOwnershipAndOnlyPermitsRetryableStates() throws Exception {
        Account owner = createAccount();
        Account otherUser = createAccount();
        long resumeId = insertResume(owner.id(), "Owner resume", "Owner raw text.");

        mockMvc.perform(post("/resumes/{resumeId}/parse", resumeId).cookie(sessionCookie(otherUser.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertEquals("NOT_STARTED", parseStatus("resume", resumeId));

        Cookie ownerCookie = sessionCookie(owner.id());
        mockMvc.perform(post("/resumes/{resumeId}/parse", resumeId).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("COMPLETED"));
        mockMvc.perform(post("/resumes/{resumeId}/parse", resumeId).cookie(ownerCookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_RESOURCE_STATE"));
    }

    private Account createAccount() {
        RegisteredUser user = userAccountService.register(
                "d03-" + UUID.randomUUID() + "@example.com",
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

    private long insertResume(long userId, String title, String rawText) {
        return jdbcTemplate.queryForObject(
                "insert into resume (user_id, title, raw_text) values (?, ?, ?) returning id",
                Long.class,
                userId,
                title,
                rawText
        );
    }

    private long insertJobDescription(long userId, String title, String rawText) {
        return jdbcTemplate.queryForObject(
                "insert into job_description (user_id, title, raw_text) values (?, ?, ?) returning id",
                Long.class,
                userId,
                title,
                rawText
        );
    }

    private String rawText(String tableName, long documentId) {
        return jdbcTemplate.queryForObject(
                "select raw_text from " + tableName + " where id = ?",
                String.class,
                documentId
        );
    }

    private String parseStatus(String tableName, long documentId) {
        return jdbcTemplate.queryForObject(
                "select parse_status from " + tableName + " where id = ?",
                String.class,
                documentId
        );
    }

    private String parsedJson(String tableName, long documentId) {
        return jdbcTemplate.queryForObject(
                "select parsed_json::text from " + tableName + " where id = ?",
                String.class,
                documentId
        );
    }

    private String parsedJsonField(String tableName, long documentId, String field, Integer arrayIndex) {
        String path = arrayIndex == null ? field : field + "," + arrayIndex;
        return jdbcTemplate.queryForObject(
                "select parsed_json #>> string_to_array(?, ',') from " + tableName + " where id = ?",
                String.class,
                path,
                documentId
        );
    }

    private record Account(long id) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeDocumentParserConfiguration {

        @Bean
        @Primary
        FakeDocumentParser documentParser() {
            return new FakeDocumentParser();
        }
    }

    static class FakeDocumentParser implements DocumentParser {

        private String resumeResponse;
        private String jobDescriptionResponse;
        private RuntimeException failure;

        @Override
        public String parseResume(String rawText) {
            throwIfConfigured();
            return resumeResponse;
        }

        @Override
        public String parseJobDescription(String rawText) {
            throwIfConfigured();
            return jobDescriptionResponse;
        }

        private void throwIfConfigured() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
