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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@Tag("external")
@Transactional
class DocumentSchemaMigrationTest {

    private static final String SYNTHETIC_PASSWORD_HASH =
            "$2a$12$syntheticHashValueForSchemaTestsOnly000000000000000000";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsOnlyTheDocumentedResumeColumns() {
        Set<String> columns = tableColumns("resume");

        assertEquals(Set.of(
                "id",
                "user_id",
                "title",
                "raw_text",
                "parsed_json",
                "parse_status",
                "parse_error",
                "created_at",
                "updated_at"
        ), columns);
        assertNoDeferredStorageColumns(columns);
    }

    @Test
    void createsOnlyTheDocumentedJobDescriptionColumns() {
        Set<String> columns = tableColumns("job_description");

        assertEquals(Set.of(
                "id",
                "user_id",
                "title",
                "company_name",
                "role_title",
                "raw_text",
                "parsed_json",
                "parse_status",
                "parse_error",
                "created_at",
                "updated_at"
        ), columns);
        assertNoDeferredStorageColumns(columns);
    }

    @Test
    void createsKnownOwnershipIndexesInColumnOrder() {
        assertEquals(
                "CREATE INDEX ix_resume_user_created_at ON public.resume USING btree (user_id, created_at)",
                indexDefinition("ix_resume_user_created_at")
        );
        assertEquals(
                "CREATE INDEX ix_job_description_user_created_at ON public.job_description USING btree (user_id, created_at)",
                indexDefinition("ix_job_description_user_created_at")
        );
    }

    @Test
    void defaultsNewDocumentsToNotStarted() {
        Long userId = insertUser("document-owner@example.com");
        Long resumeId = jdbcTemplate.queryForObject(
                "insert into resume (user_id, title, raw_text) values (?, ?, ?) returning id",
                Long.class,
                userId,
                "Backend resume",
                "Synthetic resume text"
        );
        Long jobDescriptionId = jdbcTemplate.queryForObject(
                "insert into job_description (user_id, title, raw_text) values (?, ?, ?) returning id",
                Long.class,
                userId,
                "Backend role",
                "Synthetic job description text"
        );

        assertEquals("NOT_STARTED", parseStatus("resume", resumeId));
        assertEquals("NOT_STARTED", parseStatus("job_description", jobDescriptionId));
    }

    @Test
    void rejectsUnknownResumeParseStatus() {
        Long userId = insertUser("resume-status-owner@example.com");
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "insert into resume (user_id, title, raw_text, parse_status) values (?, ?, ?, ?)",
                userId,
                "Resume",
                "Synthetic resume text",
                "UNKNOWN"
        ));
    }

    @Test
    void rejectsUnknownJobDescriptionParseStatus() {
        Long userId = insertUser("job-status-owner@example.com");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "insert into job_description (user_id, title, raw_text, parse_status) values (?, ?, ?, ?)",
                userId,
                "Role",
                "Synthetic job description text",
                "UNKNOWN"
        ));
    }

    @Test
    void rejectsBlankResumeTitle() {
        Long userId = insertUser("blank-resume-owner@example.com");
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "insert into resume (user_id, title, raw_text) values (?, ?, ?)",
                userId,
                "   ",
                "Synthetic resume text"
        ));
    }

    @Test
    void rejectsBlankResumeRawText() {
        Long userId = insertUser("blank-resume-text-owner@example.com");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "insert into resume (user_id, title, raw_text) values (?, ?, ?)",
                userId,
                "Resume",
                "   "
        ));
    }

    @Test
    void rejectsBlankJobDescriptionTitle() {
        Long userId = insertUser("blank-job-title-owner@example.com");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "insert into job_description (user_id, title, raw_text) values (?, ?, ?)",
                userId,
                "   ",
                "Synthetic job description text"
        ));
    }

    @Test
    void rejectsBlankJobDescriptionRawText() {
        Long userId = insertUser("blank-job-owner@example.com");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "insert into job_description (user_id, title, raw_text) values (?, ?, ?)",
                userId,
                "Role",
                "   "
        ));
    }

    @Test
    void rejectsResumeWithoutAnOwner() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "insert into resume (user_id, title, raw_text) values (?, ?, ?)",
                Long.MAX_VALUE,
                "Resume",
                "Synthetic resume text"
        ));
    }

    @Test
    void rejectsJobDescriptionWithoutAnOwner() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "insert into job_description (user_id, title, raw_text) values (?, ?, ?)",
                Long.MAX_VALUE,
                "Role",
                "Synthetic job description text"
        ));
    }

    private Set<String> tableColumns(String tableName) {
        List<String> columns = jdbcTemplate.queryForList(
                """
                select column_name
                from information_schema.columns
                where table_schema = 'public' and table_name = ?
                """,
                String.class,
                tableName
        );
        return new HashSet<>(columns);
    }

    private String indexDefinition(String indexName) {
        return jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where schemaname = 'public' and indexname = ?",
                String.class,
                indexName
        );
    }

    private String parseStatus(String tableName, Long id) {
        return jdbcTemplate.queryForObject(
                "select parse_status from " + tableName + " where id = ?",
                String.class,
                id
        );
    }

    private void assertNoDeferredStorageColumns(Set<String> columns) {
        assertFalse(columns.contains("file_name"));
        assertFalse(columns.contains("file_path"));
        assertFalse(columns.contains("file_url"));
        assertFalse(columns.contains("embedding"));
        assertFalse(columns.contains("document_chunks"));
    }

    private Long insertUser(String email) {
        return jdbcTemplate.queryForObject(
                "insert into app_user (email, password_hash) values (?, ?) returning id",
                Long.class,
                email,
                SYNTHETIC_PASSWORD_HASH
        );
    }
}
