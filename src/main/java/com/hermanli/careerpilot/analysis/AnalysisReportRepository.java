package com.hermanli.careerpilot.analysis;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class AnalysisReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalysisReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long create(long userId, long resumeId, long jobDescriptionId) {
        return jdbcTemplate.queryForObject(
                """
                insert into analysis_report (user_id, resume_id, job_description_id)
                values (?, ?, ?)
                returning id
                """,
                Long.class,
                userId,
                resumeId,
                jobDescriptionId
        );
    }

    public boolean markRunning(long analysisId, long userId) {
        return jdbcTemplate.update(
                """
                update analysis_report
                set status = 'RUNNING', started_at = current_timestamp, updated_at = current_timestamp
                where id = ? and user_id = ? and status = 'PENDING'
                """,
                analysisId,
                userId
        ) == 1;
    }

    public boolean markCompleted(long analysisId, long userId, MatchReport report, String reportJson) {
        return jdbcTemplate.update(
                """
                update analysis_report
                set status = 'COMPLETED', match_score = ?, report_json = cast(? as jsonb),
                    completed_at = current_timestamp, updated_at = current_timestamp,
                    error_code = null, error_message = null
                where id = ? and user_id = ? and status = 'RUNNING'
                """,
                report.matchScore(),
                reportJson,
                analysisId,
                userId
        ) == 1;
    }

    public void markFailed(long analysisId, long userId, String errorCode, String errorMessage) {
        jdbcTemplate.update(
                """
                update analysis_report
                set status = 'FAILED', error_code = ?, error_message = ?, updated_at = current_timestamp
                where id = ? and user_id = ? and status = 'RUNNING'
                """,
                errorCode,
                errorMessage,
                analysisId,
                userId
        );
    }

    public Optional<StoredAnalysisReport> findByIdAndUserId(long analysisId, long userId) {
        return jdbcTemplate.query(
                selectReports() + " where ar.id = ? and ar.user_id = ?",
                (resultSet, rowNumber) -> mapReport(resultSet),
                analysisId,
                userId
        ).stream().findFirst();
    }

    public List<StoredAnalysisReport> findAllByUserId(long userId) {
        return jdbcTemplate.query(
                selectReports() + " where ar.user_id = ? order by ar.created_at desc, ar.id desc",
                (resultSet, rowNumber) -> mapReport(resultSet),
                userId
        );
    }

    public int markAbandonedAsFailed() {
        return jdbcTemplate.update(
                """
                update analysis_report
                set status = 'FAILED', error_code = 'ANALYSIS_INTERRUPTED',
                    error_message = 'The analysis was interrupted. Please try again.',
                    updated_at = current_timestamp
                where status in ('PENDING', 'RUNNING')
                """
        );
    }

    private String selectReports() {
        return """
                select ar.id, ar.resume_id, ar.job_description_id, ar.status, ar.match_score, ar.report_json::text,
                       ar.error_code, ar.error_message, ar.created_at, ar.started_at, ar.completed_at, cp.id as plan_id
                from analysis_report ar
                left join career_plan cp on cp.analysis_report_id = ar.id
                """;
    }

    private StoredAnalysisReport mapReport(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new StoredAnalysisReport(
                resultSet.getLong("id"),
                resultSet.getLong("resume_id"),
                resultSet.getLong("job_description_id"),
                AnalysisStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("match_score", Integer.class),
                resultSet.getString("report_json"),
                resultSet.getObject("plan_id", Long.class),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                resultSet.getTimestamp("created_at").toInstant(),
                instantOrNull(resultSet, "started_at"),
                instantOrNull(resultSet, "completed_at")
        );
    }

    private Instant instantOrNull(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        java.sql.Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record StoredAnalysisReport(
            long id,
            long resumeId,
            long jobDescriptionId,
            AnalysisStatus status,
            Integer matchScore,
            String reportJson,
            Long planId,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt
    ) {
    }
}
