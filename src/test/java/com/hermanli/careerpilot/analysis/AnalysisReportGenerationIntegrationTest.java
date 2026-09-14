package com.hermanli.careerpilot.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.hermanli.careerpilot.plan.PlanGenerator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@Import(AnalysisReportGenerationIntegrationTest.FakeReportGeneratorConfiguration.class)
@Tag("external")
@Transactional
class AnalysisReportGenerationIntegrationTest {

    private static final String SYNTHETIC_PASSWORD_HASH =
            "$2a$12$syntheticHashValueForSchemaTestsOnly000000000000000000";
    private static final String VALID_REPORT_JSON = """
            {
              "matchScore":72,
              "matchedSkills":["Java"],
              "partialMatches":[],
              "missingSkills":["Docker"],
              "strengths":["Java project"],
              "risks":["Docker requirement"],
              "recommendations":["Add Docker deployment evidence"]
            }
            """;
    private static final String VALID_PLAN_JSON = """
            {
              "title":"14-Day Preparation Plan",
              "summary":"Address evidence-based gaps.",
              "tasks":[{
                "title":"Add deployment evidence",
                "description":"Document a small deployment exercise.",
                "dayOffset":1,
                "priority":"HIGH",
                "sourceEvidence":"Docker"
              }]
            }
            """;

    @Autowired
    private AnalysisReportService analysisReportService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeReportGenerator reportGenerator;

    @Autowired
    private FakePlanGenerator planGenerator;

    @BeforeEach
    void resetGenerator() {
        reportGenerator.response = VALID_REPORT_JSON;
        reportGenerator.failure = null;
        reportGenerator.calls = 0;
        planGenerator.response = VALID_PLAN_JSON;
        planGenerator.calls = 0;
    }

    @Test
    void persistsTheCompletedLifecycleAndValidatedReport() {
        Inputs inputs = insertParsedInputs();

        long analysisId = analysisReportService.generate(inputs.userId(), inputs.resumeId(), inputs.jobDescriptionId());

        assertEquals("COMPLETED", status(analysisId));
        assertEquals(50, matchScore(analysisId));
        assertEquals("Java", reportJsonValue(analysisId, "matchedSkills,0"));
        assertEquals(1, reportGenerator.calls);
        assertEquals(1, planCount(analysisId));
        assertEquals(1, planTaskCount(analysisId));
        assertEquals(1, planGenerator.calls);
    }

    @Test
    void malformedOutputIsRetriedThenPersistedAsSafeFailure() {
        Inputs inputs = insertParsedInputs();
        reportGenerator.response = "{\"matchScore\":101}";

        long analysisId = analysisReportService.generate(inputs.userId(), inputs.resumeId(), inputs.jobDescriptionId());

        assertEquals("FAILED", status(analysisId));
        assertEquals("INVALID_REPORT_CONSTRAINTS", errorCode(analysisId));
        assertEquals(2, reportGenerator.calls);
    }

    @Test
    void rerunningCreatesImmutableHistoricalReports() {
        Inputs inputs = insertParsedInputs();

        long firstAnalysisId = analysisReportService.generate(inputs.userId(), inputs.resumeId(), inputs.jobDescriptionId());
        long secondAnalysisId = analysisReportService.generate(inputs.userId(), inputs.resumeId(), inputs.jobDescriptionId());

        assertEquals("COMPLETED", status(firstAnalysisId));
        assertEquals("COMPLETED", status(secondAnalysisId));
        assertEquals(2, analysisCount(inputs.userId()));
    }

    private Inputs insertParsedInputs() {
        long userId = jdbcTemplate.queryForObject(
                "insert into app_user (email, password_hash) values (?, ?) returning id",
                Long.class,
                "analysis-" + UUID.randomUUID() + "@example.com",
                SYNTHETIC_PASSWORD_HASH
        );
        long resumeId = jdbcTemplate.queryForObject(
                """
                insert into resume (user_id, title, raw_text, parsed_json, parse_status)
                values (?, ?, ?, cast(? as jsonb), 'COMPLETED') returning id
                """,
                Long.class,
                userId,
                "Synthetic resume",
                "Synthetic resume text",
                "{\"skills\":[\"Java\"],\"projects\":[\"Deployment project\"]}"
        );
        long jobDescriptionId = jdbcTemplate.queryForObject(
                """
                insert into job_description (user_id, title, raw_text, parsed_json, parse_status)
                values (?, ?, ?, cast(? as jsonb), 'COMPLETED') returning id
                """,
                Long.class,
                userId,
                "Synthetic role",
                "Synthetic job description text",
                "{\"requiredSkills\":[\"Java\",\"Docker\"],\"responsibilities\":[\"Deploy Java APIs\"]}"
        );
        return new Inputs(userId, resumeId, jobDescriptionId);
    }

    private String status(long analysisId) {
        return jdbcTemplate.queryForObject(
                "select status from analysis_report where id = ?", String.class, analysisId
        );
    }

    private int matchScore(long analysisId) {
        return jdbcTemplate.queryForObject(
                "select match_score from analysis_report where id = ?", Integer.class, analysisId
        );
    }

    private String reportJsonValue(long analysisId, String path) {
        return jdbcTemplate.queryForObject(
                "select report_json #>> string_to_array(?, ',') from analysis_report where id = ?",
                String.class,
                path,
                analysisId
        );
    }

    private String errorCode(long analysisId) {
        return jdbcTemplate.queryForObject(
                "select error_code from analysis_report where id = ?", String.class, analysisId
        );
    }

    private int analysisCount(long userId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from analysis_report where user_id = ?", Integer.class, userId
        );
    }

    private int planCount(long analysisId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from career_plan where analysis_report_id = ?", Integer.class, analysisId
        );
    }

    private int planTaskCount(long analysisId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from plan_task task join career_plan plan on plan.id = task.career_plan_id where plan.analysis_report_id = ?",
                Integer.class, analysisId
        );
    }

    private record Inputs(long userId, long resumeId, long jobDescriptionId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeReportGeneratorConfiguration {

        @Bean
        @Primary
        FakeReportGenerator reportGenerator() {
            return new FakeReportGenerator();
        }

        @Bean
        @Primary
        FakePlanGenerator planGenerator() {
            return new FakePlanGenerator();
        }
    }

    static class FakeReportGenerator implements ReportGenerator {

        private String response;
        private RuntimeException failure;
        private int calls;

        @Override
        public String generate(String resumeParsedJson, String jobDescriptionParsedJson) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }

    static class FakePlanGenerator implements PlanGenerator {

        private String response;
        private int calls;

        @Override
        public String generate(MatchReport report, String jobDescriptionParsedJson, String priorTaskProgressJson) {
            calls++;
            return response;
        }
    }
}
