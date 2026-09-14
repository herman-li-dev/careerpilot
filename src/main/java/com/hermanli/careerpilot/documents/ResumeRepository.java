package com.hermanli.careerpilot.documents;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ResumeRepository {

    private final JdbcTemplate jdbcTemplate;

    public ResumeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Resume create(long userId, String title, String rawText) {
        return jdbcTemplate.queryForObject(
                """
                insert into resume (user_id, title, raw_text)
                values (?, ?, ?)
                returning id, title, raw_text, parse_status, parse_error, created_at, updated_at
                """,
                (resultSet, rowNumber) -> mapResume(resultSet),
                userId,
                title,
                rawText
        );
    }

    public List<Resume> findAllByUserId(long userId) {
        return jdbcTemplate.query(
                """
                select id, title, raw_text, parse_status, parse_error, created_at, updated_at
                from resume
                where user_id = ?
                order by created_at desc, id desc
                """,
                (resultSet, rowNumber) -> mapResume(resultSet),
                userId
        );
    }

    public Optional<Resume> findByIdAndUserId(long resumeId, long userId) {
        return jdbcTemplate.query(
                """
                select id, title, raw_text, parse_status, parse_error, created_at, updated_at
                from resume
                where id = ? and user_id = ?
                """,
                (resultSet, rowNumber) -> mapResume(resultSet),
                resumeId,
                userId
        ).stream().findFirst();
    }

    public Optional<String> findCompletedParsedJsonByIdAndUserId(long resumeId, long userId) {
        return jdbcTemplate.query(
                """
                select parsed_json::text
                from resume
                where id = ? and user_id = ? and parse_status = 'COMPLETED' and parsed_json is not null
                """,
                (resultSet, rowNumber) -> resultSet.getString("parsed_json"),
                resumeId,
                userId
        ).stream().findFirst();
    }

    public boolean deleteByIdAndUserId(long resumeId, long userId) {
        return jdbcTemplate.update(
                "delete from resume where id = ? and user_id = ?",
                resumeId,
                userId
        ) == 1;
    }

    public boolean markRunning(long resumeId, long userId) {
        return jdbcTemplate.update(
                """
                update resume
                set parse_status = 'RUNNING', parse_error = null, updated_at = current_timestamp
                where id = ? and user_id = ? and parse_status in ('NOT_STARTED', 'FAILED')
                """,
                resumeId,
                userId
        ) == 1;
    }

    public void markCompleted(long resumeId, long userId, String parsedJson) {
        jdbcTemplate.update(
                """
                update resume
                set parsed_json = cast(? as jsonb), parse_status = 'COMPLETED', parse_error = null,
                    updated_at = current_timestamp
                where id = ? and user_id = ? and parse_status = 'RUNNING'
                """,
                parsedJson,
                resumeId,
                userId
        );
    }

    public void markFailed(long resumeId, long userId, String parseError) {
        jdbcTemplate.update(
                """
                update resume
                set parse_status = 'FAILED', parse_error = ?, updated_at = current_timestamp
                where id = ? and user_id = ? and parse_status = 'RUNNING'
                """,
                parseError,
                resumeId,
                userId
        );
    }

    private Resume mapResume(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new Resume(
                resultSet.getLong("id"),
                resultSet.getString("title"),
                resultSet.getString("raw_text"),
                resultSet.getString("parse_status"),
                resultSet.getString("parse_error"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
