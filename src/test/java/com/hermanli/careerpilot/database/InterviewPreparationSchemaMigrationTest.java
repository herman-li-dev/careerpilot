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
class InterviewPreparationSchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsOnlyDocumentedImmutableTablesAndConstraints() {
        assertEquals(Set.of("id", "user_id", "analysis_report_id", "title", "created_at"), columns("interview_session"));
        assertEquals(Set.of("id", "interview_session_id", "question_order", "question_type", "question_text",
                "assessment_goal", "source_evidence", "preparation_tip", "created_at"), columns("interview_question"));
        long userId = jdbcTemplate.queryForObject(
                "insert into app_user (email, password_hash) values ('interview-schema@example.com', 'synthetic') returning id", Long.class);
        long resumeId = jdbcTemplate.queryForObject("insert into resume (user_id, title, raw_text) values (?, 'r', 't') returning id", Long.class, userId);
        long jdId = jdbcTemplate.queryForObject("insert into job_description (user_id, title, raw_text) values (?, 'j', 't') returning id", Long.class, userId);
        long analysisId = jdbcTemplate.queryForObject(
                "insert into analysis_report (user_id, resume_id, job_description_id) values (?, ?, ?) returning id", Long.class,
                userId, resumeId, jdId);
        long sessionId = jdbcTemplate.queryForObject(
                "insert into interview_session (user_id, analysis_report_id, title) values (?, ?, 'Synthetic') returning id", Long.class,
                userId, analysisId);

        assertConstraintViolation("duplicate_session", () -> jdbcTemplate.update(
                "insert into interview_session (user_id, analysis_report_id, title) values (?, ?, 'Again')", userId, analysisId));
        assertConstraintViolation("question_order", () -> jdbcTemplate.update(
                "insert into interview_question (interview_session_id, question_order, question_type, question_text, assessment_goal, source_evidence, preparation_tip) values (?, 9, 'TECHNICAL_GAP', 'q', 'g', 'e', 't')", sessionId));
        assertConstraintViolation("question_type", () -> jdbcTemplate.update(
                "insert into interview_question (interview_session_id, question_order, question_type, question_text, assessment_goal, source_evidence, preparation_tip) values (?, 1, 'INVALID', 'q', 'g', 'e', 't')", sessionId));
    }

    @Test
    void createsDocumentedIndexesInColumnOrder() {
        assertEquals("CREATE INDEX ix_interview_session_user_created_at ON public.interview_session USING btree (user_id, created_at)",
                index("ix_interview_session_user_created_at"));
        assertEquals("CREATE INDEX ix_interview_question_session_order ON public.interview_question USING btree (interview_session_id, question_order)",
                index("ix_interview_question_session_order"));
    }

    private Set<String> columns(String table) {
        List<String> values = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_schema = 'public' and table_name = ?", String.class, table);
        return new HashSet<>(values);
    }

    private String index(String name) {
        return jdbcTemplate.queryForObject("select indexdef from pg_indexes where schemaname = 'public' and indexname = ?",
                String.class, name);
    }

    private void assertConstraintViolation(String savepoint, Runnable action) {
        jdbcTemplate.execute("savepoint " + savepoint);
        assertThrows(DataIntegrityViolationException.class, action::run);
        jdbcTemplate.execute("rollback to savepoint " + savepoint);
    }
}
