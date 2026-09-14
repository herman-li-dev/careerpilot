package com.hermanli.careerpilot.controller.careerpilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermanli.careerpilot.identity.RegisteredUser;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import com.hermanli.careerpilot.identity.UserAccountService;
import com.hermanli.careerpilot.interview.InterviewQuestionGenerator;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Import(InterviewPreparationIntegrationTest.FakeGeneratorConfiguration.class)
@Tag("external")
@Transactional
class InterviewPreparationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserAccountService userAccountService;
    @Autowired private SessionTokenService sessionTokenService;
    @Autowired private FakeGenerator generator;

    @BeforeEach
    void resetGenerator() {
        generator.response = VALID_JSON;
    }

    @Test
    void createsOrderedQuestionsThenReturnsTheSameImmutableSessionAndHidesItFromOtherUsers() throws Exception {
        long ownerId = createUser();
        long otherId = createUser();
        long analysisId = completedAnalysis(ownerId);

        JsonNode created = data(mockMvc.perform(post("/analyses/{id}/interview-prep", analysisId).cookie(cookie(ownerId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.questions.length()").value(5))
                .andReturn());
        long sessionId = created.path("id").asLong();
        assertEquals(5, jdbcTemplate.queryForObject(
                "select count(*) from interview_question where interview_session_id = ?", Integer.class, sessionId));
        assertEquals((short) 1, jdbcTemplate.queryForObject(
                "select min(question_order) from interview_question where interview_session_id = ?", Short.class, sessionId));
        assertEquals((short) 5, jdbcTemplate.queryForObject(
                "select max(question_order) from interview_question where interview_session_id = ?", Short.class, sessionId));

        mockMvc.perform(post("/analyses/{id}/interview-prep", analysisId).cookie(cookie(ownerId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(sessionId));
        mockMvc.perform(get("/interview-sessions/{id}", sessionId).cookie(cookie(ownerId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.questions[0].questionOrder").value(1));
        mockMvc.perform(post("/analyses/{id}/interview-prep", analysisId).cookie(cookie(otherId)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/interview-sessions/{id}", sessionId).cookie(cookie(otherId)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rejectsIncompleteAnalysisAndDoesNotPersistAfterRepeatedInvalidOutput() throws Exception {
        long userId = createUser();
        long pending = pendingAnalysis(userId);
        mockMvc.perform(post("/analyses/{id}/interview-prep", pending).cookie(cookie(userId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_RESOURCE_STATE"));

        long completed = completedAnalysis(userId);
        generator.response = "{\"questions\":[]}";
        mockMvc.perform(post("/analyses/{id}/interview-prep", completed).cookie(cookie(userId)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("MODEL_OUTPUT_INVALID"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from interview_session where analysis_report_id = ?", Integer.class, completed));
    }

    private long createUser() {
        RegisteredUser user = userAccountService.register("interview-" + UUID.randomUUID() + "@example.com", "Synthetic-password-123");
        return user.id();
    }

    private Cookie cookie(long userId) {
        return new Cookie(SessionCookieService.COOKIE_NAME, sessionTokenService.issue(userId));
    }

    private long completedAnalysis(long userId) {
        long resumeId = jdbcTemplate.queryForObject("""
                insert into resume (user_id, title, raw_text, parsed_json, parse_status)
                values (?, 'Synthetic resume', 'Synthetic text', cast(? as jsonb), 'COMPLETED') returning id
                """, Long.class, userId, "{\"projects\":[\"Java API project\"],\"workExperience\":[\"Collaborated with a team\"]}");
        long jdId = jdbcTemplate.queryForObject("""
                insert into job_description (user_id, title, raw_text, parsed_json, parse_status)
                values (?, 'Synthetic role', 'Synthetic text', cast(? as jsonb), 'COMPLETED') returning id
                """, Long.class, userId, "{\"requiredSkills\":[\"Docker\"],\"responsibilities\":[\"Deploy services\"]}");
        return jdbcTemplate.queryForObject("""
                insert into analysis_report (user_id, resume_id, job_description_id, status, report_json)
                values (?, ?, ?, 'COMPLETED', cast(? as jsonb)) returning id
                """, Long.class, userId, resumeId, jdId,
                "{\"matchScore\":50,\"matchedSkills\":[],\"partialMatches\":[],\"missingSkills\":[\"Docker\"],\"strengths\":[],\"risks\":[],\"recommendations\":[]}");
    }

    private long pendingAnalysis(long userId) {
        long resumeId = jdbcTemplate.queryForObject("insert into resume (user_id, title, raw_text) values (?, 'r', 't') returning id", Long.class, userId);
        long jdId = jdbcTemplate.queryForObject("insert into job_description (user_id, title, raw_text) values (?, 'j', 't') returning id", Long.class, userId);
        return jdbcTemplate.queryForObject("insert into analysis_report (user_id, resume_id, job_description_id) values (?, ?, ?) returning id",
                Long.class, userId, resumeId, jdId);
    }

    private JsonNode data(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeGeneratorConfiguration {
        @Bean @Primary FakeGenerator interviewQuestionGenerator() { return new FakeGenerator(); }
    }

    static class FakeGenerator implements InterviewQuestionGenerator {
        private String response;
        @Override public String generate(String evidenceContextJson) { return response; }
    }

    private static final String VALID_JSON = """
            {"questions":[
              {"questionType":"TECHNICAL_GAP","questionText":"What would you verify for Docker?","assessmentGoal":"Assess the documented gap.","sourceEvidence":"Docker","preparationTip":"Review the documented requirement."},
              {"questionType":"TECHNICAL_GAP","questionText":"How would you prepare for deploy services?","assessmentGoal":"Assess requirement understanding.","sourceEvidence":"Deploy services","preparationTip":"Map the requirement to real evidence."},
              {"questionType":"PROJECT_FOLLOW_UP","questionText":"Tell us about the Java API project.","assessmentGoal":"Assess the real project evidence.","sourceEvidence":"Java API project","preparationTip":"Use only the documented project details."},
              {"questionType":"BEHAVIORAL_EVIDENCE","questionText":"Describe collaborating with a team.","assessmentGoal":"Assess the real collaboration evidence.","sourceEvidence":"Collaborated with a team","preparationTip":"Describe only the documented collaboration."},
              {"questionType":"TECHNICAL_GAP","questionText":"Which Docker evidence needs verification?","assessmentGoal":"Assess evidence awareness.","sourceEvidence":"Docker","preparationTip":"Keep the preparation factual."}
            ]}
            """;
}
