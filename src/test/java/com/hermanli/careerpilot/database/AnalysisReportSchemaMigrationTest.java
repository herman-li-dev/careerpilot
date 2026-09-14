package com.hermanli.careerpilot.database;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@Tag("external")
@Transactional
class AnalysisReportSchemaMigrationTest {

    private static final String SYNTHETIC_PASSWORD_HASH =
            "$2a$12$syntheticHashValueForSchemaTestsOnly000000000000000000";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsOnlyTheDocumentedAnalysisReportColumns() {
        assertEquals(Set.of(
                "id", "user_id", "resume_id", "job_description_id", "status", "match_score",
                "report_json", "question_context_json", "model_name", "error_code", "error_message",
                "created_at", "started_at", "completed_at", "updated_at"
        ), tableColumns());
    }

    @Test
    void defaultsToPendingAndAcceptsScoreBoundaries() {
        Inputs inputs = insertInputs("analysis-boundary-owner@example.com");

        long pendingId = insertReport(inputs, null);
        assertEquals("PENDING", reportStatus(pendingId));
        insertReport(inputs, 0);
        insertReport(inputs, 100);
    }

    @Test
    void rejectsScoresOutsideTheDocumentedRange() {
        Inputs inputs = insertInputs("analysis-score-owner@example.com");

        assertThrows(DataIntegrityViolationException.class, () -> insertReport(inputs, -1));
        assertThrows(DataIntegrityViolationException.class, () -> insertReport(inputs, 101));
    }

    @Test
    void rejectsUnknownLifecycleStatus() {
        Inputs inputs = insertInputs("analysis-status-owner@example.com");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "insert into analysis_report (user_id, resume_id, job_description_id, status) values (?, ?, ?, ?)",
                inputs.userId(), inputs.resumeId(), inputs.jobDescriptionId(), "UNKNOWN"
        ));
    }

    @Test
    void createsTheKnownHistoryIndexInColumnOrder() {
        assertEquals(
                "CREATE INDEX ix_analysis_report_user_status_created_at ON public.analysis_report USING btree (user_id, status, created_at)",
                jdbcTemplate.queryForObject(
                        "select indexdef from pg_indexes where schemaname = 'public' and indexname = ?",
                        String.class,
                        "ix_analysis_report_user_status_created_at"
                )
        );
    }

    private Set<String> tableColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_schema = 'public' and table_name = 'analysis_report'",
                String.class
        );
        return new HashSet<>(columns);
    }

    private long insertReport(Inputs inputs, Integer score) {
        return jdbcTemplate.queryForObject(
                "insert into analysis_report (user_id, resume_id, job_description_id, match_score) values (?, ?, ?, ?) returning id",
                Long.class,
                inputs.userId(), inputs.resumeId(), inputs.jobDescriptionId(), score
        );
    }

    private String reportStatus(long reportId) {
        return jdbcTemplate.queryForObject(
                "select status from analysis_report where id = ?",
                String.class,
                reportId
        );
    }

    private Inputs insertInputs(String email) {
        long userId = jdbcTemplate.queryForObject(
                "insert into app_user (email, password_hash) values (?, ?) returning id",
                Long.class, email, SYNTHETIC_PASSWORD_HASH
        );
        long resumeId = jdbcTemplate.queryForObject(
                "insert into resume (user_id, title, raw_text) values (?, ?, ?) returning id",
                Long.class, userId, "Synthetic resume", "Synthetic resume text"
        );
        long jobDescriptionId = jdbcTemplate.queryForObject(
                "insert into job_description (user_id, title, raw_text) values (?, ?, ?) returning id",
                Long.class, userId, "Synthetic role", "Synthetic job description text"
        );
        return new Inputs(userId, resumeId, jobDescriptionId);
    }

    private record Inputs(long userId, long resumeId, long jobDescriptionId) {
    }
}
