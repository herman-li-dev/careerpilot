package com.hermanli.careerpilot.controller.careerpilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermanli.careerpilot.analysis.MatchReport;
import com.hermanli.careerpilot.analysis.ReportGenerator;
import com.hermanli.careerpilot.documents.DocumentParser;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.plan.PlanGenerator;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Import(CareerPilotV1EndToEndIntegrationTest.FakeModelConfiguration.class)
@Tag("external")
class CareerPilotV1EndToEndIntegrationTest {

    private static final String VALID_RESUME_JSON = """
            {
              "skills": ["Java"],
              "education": ["Computer Science student"],
              "projects": ["Built a Java REST API"],
              "workExperience": [],
              "certifications": []
            }
            """;
    private static final String VALID_JOB_DESCRIPTION_JSON = """
            {
              "companyName": "Example Company",
              "roleTitle": "Backend Developer Co-op",
              "location": "Vancouver, BC",
              "responsibilities": ["Build and deploy Java services"],
              "requiredSkills": ["Java", "AWS", "TDD", "Docker"],
              "preferredSkills": [],
              "experienceRequirements": ["Evidence from software projects"]
            }
            """;
    private static final String VALID_REPORT_JSON = """
            {
              "matchScore": 25,
              "matchedSkills": ["Programming"],
              "partialMatches": [],
              "missingSkills": ["Cloud Computing", "Test Driven Development", "DevOps and Software Delivery"],
              "strengths": ["Built a Java REST API"],
              "risks": ["Cloud evidence is missing", "TDD evidence is missing", "Deployment evidence is missing"],
              "recommendations": ["Verify the available evidence before making application claims"]
            }
            """;
    private static final String UNSUPPORTED_PLAN_JSON = """
            {
              "title": "Unsupported model plan",
              "summary": "This output must not be persisted.",
              "tasks": [{
                "title": "Invent a Kubernetes outage story",
                "description": "Claim an incident that is absent from the source evidence.",
                "dayOffset": 1,
                "priority": "HIGH",
                "sourceEvidence": "Cloud Computing",
                "focusArea": "CLOUD_COMPUTING",
                "taskType": "INTERVIEW_STORY",
                "deliverable": "One fictional incident story"
              }]
            }
            """;
    private static final String REGENERATED_PLAN_JSON = """
            {
              "title": "14-Day Preparation Plan",
              "summary": "Replace only the remaining evidence-verification work.",
              "tasks": [
                {
                  "title": "Document the DevOps evidence decision",
                  "description": "Verify whether real resume or project evidence supports this requirement without adding unsupported details.",
                  "dayOffset": 2,
                  "priority": "MEDIUM",
                  "sourceEvidence": "DevOps and Software Delivery",
                  "focusArea": "DEVOPS_DELIVERY",
                  "taskType": "EVIDENCE_VERIFICATION",
                  "deliverable": "One verified evidence-to-requirement mapping with a yes/no conclusion"
                },
                {
                  "title": "Document the TDD evidence decision",
                  "description": "Verify whether real resume or project evidence supports this requirement without adding unsupported details.",
                  "dayOffset": 4,
                  "priority": "LOW",
                  "sourceEvidence": "Test Driven Development",
                  "focusArea": "TDD",
                  "taskType": "EVIDENCE_VERIFICATION",
                  "deliverable": "One verified evidence-to-requirement mapping with a yes/no conclusion"
                }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakePlanGenerator planGenerator;

    private final List<Long> createdUserIds = new ArrayList<>();
    private Long createdAnalysisId;

    @BeforeEach
    void resetFakePlanGenerator() {
        planGenerator.calls = 0;
    }

    @AfterEach
    void removeSyntheticAcceptanceData() {
        awaitTerminalAnalysisIfNecessary();
        for (long userId : createdUserIds) {
            jdbcTemplate.update(
                    "delete from plan_task where career_plan_id in (select id from career_plan where user_id = ?)",
                    userId
            );
            jdbcTemplate.update("delete from career_plan where user_id = ?", userId);
            jdbcTemplate.update("delete from analysis_report where user_id = ?", userId);
            jdbcTemplate.update("delete from resume where user_id = ?", userId);
            jdbcTemplate.update("delete from job_description where user_id = ?", userId);
            jdbcTemplate.update("delete from user_profile where user_id = ?", userId);
            jdbcTemplate.update("delete from app_user where id = ?", userId);
        }
    }

    @Test
    void acceptsTheCompletePersistedV1WorkflowWithoutCallingARealModel() throws Exception {
        Account owner = registerAndLogin("owner");
        long resumeId = createAndParseResume(owner.sessionCookie());
        long jobDescriptionId = createAndParseJobDescription(owner.sessionCookie());

        JsonNode createdAnalysis = responseData(mockMvc.perform(post("/analyses")
                        .cookie(owner.sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":%d,\"jobDescriptionId\":%d}"
                                .formatted(resumeId, jobDescriptionId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn());
        createdAnalysisId = createdAnalysis.path("analysisId").longValue();
        awaitAnalysisStatus(createdAnalysisId, "COMPLETED");

        JsonNode persistedAnalysis = responseData(mockMvc.perform(get("/analyses/{analysisId}", createdAnalysisId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.report.matchScore").value(25))
                .andExpect(jsonPath("$.data.planId").isNumber())
                .andReturn());
        long planId = persistedAnalysis.path("planId").longValue();
        String originalReportJson = reportJson(createdAnalysisId);

        MvcResult eventRequest = mockMvc.perform(get("/analyses/{analysisId}/events", createdAnalysisId)
                        .cookie(owner.sessionCookie()))
                .andExpect(request().asyncStarted())
                .andReturn();
        String stream = mockMvc.perform(asyncDispatch(eventRequest))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:report")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:plan")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(stream.indexOf("event:report") < stream.indexOf("event:plan"));
        assertTrue(stream.indexOf("event:plan") < stream.indexOf("event:done"));
        assertEquals(1, stream.split("event:done", -1).length - 1);

        JsonNode initialTasks = responseData(mockMvc.perform(get("/plans/{planId}/tasks", planId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(initialTasks.size() >= 3);
        assertTrue(initialTasks.size() <= 8);
        assertEquals(initialTasks.size(), distinctSourceEvidenceCount(initialTasks));
        assertEquals(2, planGenerator.calls);
        assertFalse(taskText(initialTasks).toLowerCase().contains("kubernetes"));

        JsonNode protectedTask = initialTasks.get(0);
        long protectedTaskId = protectedTask.path("id").longValue();
        Set<Long> replacedTaskIds = new HashSet<>();
        for (int index = 1; index < initialTasks.size(); index++) {
            replacedTaskIds.add(initialTasks.get(index).path("id").longValue());
        }
        mockMvc.perform(patch("/plans/{planId}/tasks/{taskId}", planId, protectedTaskId)
                        .cookie(owner.sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"dueDate\":\"2026-09-12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.dueDate").value("2026-09-12"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        JsonNode regeneratedTasks = responseData(mockMvc.perform(post("/plans/{planId}/regenerate-remaining", planId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals(3, regeneratedTasks.size());
        assertTrue(regeneratedTasks.size() <= 8);
        assertEquals(regeneratedTasks.size(), distinctSourceEvidenceCount(regeneratedTasks));
        assertTrue(containsTask(regeneratedTasks, protectedTaskId));
        assertTrue(replacedTaskIds.stream().noneMatch(taskId -> containsTask(regeneratedTasks, taskId)));
        assertEquals(replacedTaskIds.size(), archivedTaskCount(planId));
        assertEquals(3, planGenerator.calls);

        JsonNode refreshedTasks = responseData(mockMvc.perform(get("/plans/{planId}/tasks", planId)
                        .cookie(owner.sessionCookie()))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals(taskIds(regeneratedTasks), taskIds(refreshedTasks));
        assertTrue(refreshedTasks.findValues("archivedAt").stream().allMatch(JsonNode::isNull));
        assertEquals(originalReportJson, reportJson(createdAnalysisId));

        Account otherUser = registerAndLogin("other");
        mockMvc.perform(get("/analyses/{analysisId}", createdAnalysisId).cookie(otherUser.sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/plans/{planId}/tasks", planId).cookie(otherUser.sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private Account registerAndLogin(String label) throws Exception {
        String email = "v1-acceptance-" + label + "-" + UUID.randomUUID() + "@example.com";
        String password = "Synthetic-" + UUID.randomUUID();
        JsonNode registered = responseData(mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Credentials(email, password))))
                .andExpect(status().isCreated())
                .andReturn());
        long userId = registered.path("id").longValue();
        createdUserIds.add(userId);

        MockHttpServletResponse loginResponse = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Credentials(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId))
                .andReturn()
                .getResponse();
        Cookie sessionCookie = loginResponse.getCookie(SessionCookieService.COOKIE_NAME);
        assertNotNull(sessionCookie);
        return new Account(sessionCookie);
    }

    private long createAndParseResume(Cookie sessionCookie) throws Exception {
        JsonNode created = responseData(mockMvc.perform(post("/resumes")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Synthetic resume\",\"rawText\":\"Built a Java REST API.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parseStatus").value("NOT_STARTED"))
                .andReturn());
        long resumeId = created.path("id").longValue();
        mockMvc.perform(post("/resumes/{resumeId}/parse", resumeId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("COMPLETED"));
        return resumeId;
    }

    private long createAndParseJobDescription(Cookie sessionCookie) throws Exception {
        JsonNode created = responseData(mockMvc.perform(post("/job-descriptions")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Synthetic role\",\"rawText\":\"Java, AWS, TDD, and Docker are required.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parseStatus").value("NOT_STARTED"))
                .andReturn());
        long jobDescriptionId = created.path("id").longValue();
        mockMvc.perform(post("/job-descriptions/{jobDescriptionId}/parse", jobDescriptionId)
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("COMPLETED"));
        return jobDescriptionId;
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private void awaitAnalysisStatus(long analysisId, String expectedStatus) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            String status = jdbcTemplate.queryForObject(
                    "select status from analysis_report where id = ?", String.class, analysisId
            );
            if (expectedStatus.equals(status)) {
                return;
            }
            if ("FAILED".equals(status)) {
                throw new AssertionError("Analysis failed instead of reaching " + expectedStatus);
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Analysis did not reach " + expectedStatus + " within 10 seconds");
    }

    private void awaitTerminalAnalysisIfNecessary() {
        if (createdAnalysisId == null) {
            return;
        }
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            List<String> statuses = jdbcTemplate.queryForList(
                    "select status from analysis_report where id = ?", String.class, createdAnalysisId
            );
            if (statuses.isEmpty() || Set.of("COMPLETED", "FAILED").contains(statuses.getFirst())) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private long distinctSourceEvidenceCount(JsonNode tasks) {
        Set<String> evidence = new HashSet<>();
        tasks.forEach(task -> evidence.add(task.path("sourceEvidence").textValue()));
        return evidence.size();
    }

    private String taskText(JsonNode tasks) {
        StringBuilder text = new StringBuilder();
        tasks.forEach(task -> text.append(task.path("title").textValue())
                .append(' ')
                .append(task.path("description").textValue())
                .append(' '));
        return text.toString();
    }

    private boolean containsTask(JsonNode tasks, long taskId) {
        for (JsonNode task : tasks) {
            if (task.path("id").longValue() == taskId) {
                return true;
            }
        }
        return false;
    }

    private Set<Long> taskIds(JsonNode tasks) {
        Set<Long> ids = new HashSet<>();
        tasks.forEach(task -> ids.add(task.path("id").longValue()));
        return ids;
    }

    private int archivedTaskCount(long planId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from plan_task where career_plan_id = ? and archived_at is not null",
                Integer.class,
                planId
        );
    }

    private String reportJson(long analysisId) {
        return jdbcTemplate.queryForObject(
                "select report_json::text from analysis_report where id = ?", String.class, analysisId
        );
    }

    private record Account(Cookie sessionCookie) {
    }

    private record Credentials(String email, String password) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeModelConfiguration {

        @Bean
        @Primary
        DocumentParser documentParser() {
            return new DocumentParser() {
                @Override
                public String parseResume(String rawText) {
                    return VALID_RESUME_JSON;
                }

                @Override
                public String parseJobDescription(String rawText) {
                    return VALID_JOB_DESCRIPTION_JSON;
                }
            };
        }

        @Bean
        @Primary
        ReportGenerator reportGenerator() {
            return (resumeParsedJson, jobDescriptionParsedJson) -> VALID_REPORT_JSON;
        }

        @Bean
        @Primary
        FakePlanGenerator planGenerator() {
            return new FakePlanGenerator();
        }
    }

    static class FakePlanGenerator implements PlanGenerator {

        private int calls;

        @Override
        public String generate(MatchReport report, String jobDescriptionParsedJson, String priorTaskProgressJson) {
            return generate(report, jobDescriptionParsedJson, priorTaskProgressJson, "{}");
        }

        @Override
        public String generate(
                MatchReport report,
                String jobDescriptionParsedJson,
                String priorTaskProgressJson,
                String normalizedGapsJson
        ) {
            calls++;
            return calls <= 2 ? UNSUPPORTED_PLAN_JSON : REGENERATED_PLAN_JSON;
        }
    }
}
