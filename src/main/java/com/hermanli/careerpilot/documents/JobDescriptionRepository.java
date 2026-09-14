package com.hermanli.careerpilot.documents;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JobDescriptionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JobDescriptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public JobDescription create(long userId, String title, String rawText) {
        return jdbcTemplate.queryForObject(
                """
                insert into job_description (user_id, title, raw_text)
                values (?, ?, ?)
                returning id, title, company_name, role_title, raw_text,
                          parse_status, parse_error, created_at, updated_at
                """,
                (resultSet, rowNumber) -> mapJobDescription(resultSet),
                userId,
                title,
                rawText
        );
    }

    public List<JobDescription> findAllByUserId(long userId) {
        return jdbcTemplate.query(
                """
                select id, title, company_name, role_title, raw_text,
                       parse_status, parse_error, created_at, updated_at
                from job_description
                where user_id = ?
                order by created_at desc, id desc
                """,
                (resultSet, rowNumber) -> mapJobDescription(resultSet),
                userId
        );
    }

    public Optional<JobDescription> findByIdAndUserId(long jobDescriptionId, long userId) {
        return jdbcTemplate.query(
                """
                select id, title, company_name, role_title, raw_text,
                       parse_status, parse_error, created_at, updated_at
                from job_description
                where id = ? and user_id = ?
                """,
                (resultSet, rowNumber) -> mapJobDescription(resultSet),
                jobDescriptionId,
                userId
        ).stream().findFirst();
    }

    public Optional<String> findCompletedParsedJsonByIdAndUserId(long jobDescriptionId, long userId) {
        return jdbcTemplate.query(
                """
                select parsed_json::text
                from job_description
                where id = ? and user_id = ? and parse_status = 'COMPLETED' and parsed_json is not null
                """,
                (resultSet, rowNumber) -> resultSet.getString("parsed_json"),
                jobDescriptionId,
                userId
        ).stream().findFirst();
    }

    public boolean deleteByIdAndUserId(long jobDescriptionId, long userId) {
        return jdbcTemplate.update(
                "delete from job_description where id = ? and user_id = ?",
                jobDescriptionId,
                userId
        ) == 1;
    }

    public boolean markRunning(long jobDescriptionId, long userId) {
        return jdbcTemplate.update(
                """
                update job_description
                set parse_status = 'RUNNING', parse_error = null, updated_at = current_timestamp
                where id = ? and user_id = ? and parse_status in ('NOT_STARTED', 'FAILED')
                """,
                jobDescriptionId,
                userId
        ) == 1;
    }

    public void markCompleted(long jobDescriptionId, long userId, String parsedJson) {
        jdbcTemplate.update(
                """
                update job_description
                set parsed_json = cast(? as jsonb), parse_status = 'COMPLETED', parse_error = null,
                    updated_at = current_timestamp
                where id = ? and user_id = ? and parse_status = 'RUNNING'
                """,
                parsedJson,
                jobDescriptionId,
                userId
        );
    }

    public void markFailed(long jobDescriptionId, long userId, String parseError) {
        jdbcTemplate.update(
                """
                update job_description
                set parse_status = 'FAILED', parse_error = ?, updated_at = current_timestamp
                where id = ? and user_id = ? and parse_status = 'RUNNING'
                """,
                parseError,
                jobDescriptionId,
                userId
        );
    }

    private JobDescription mapJobDescription(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new JobDescription(
                resultSet.getLong("id"),
                resultSet.getString("title"),
                resultSet.getString("company_name"),
                resultSet.getString("role_title"),
                resultSet.getString("raw_text"),
                resultSet.getString("parse_status"),
                resultSet.getString("parse_error"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
