package com.hermanli.careerpilot.plan;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.Instant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class PlanRepository {

    private final JdbcTemplate jdbcTemplate;

    public PlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long create(long userId, long analysisReportId, PlanDraft plan) {
        return jdbcTemplate.queryForObject(
                "insert into career_plan (user_id, analysis_report_id, title, summary) values (?, ?, ?, ?) returning id",
                Long.class,
                userId,
                analysisReportId,
                plan.title(),
                plan.summary()
        );
    }

    public void createTask(long careerPlanId, PlanTaskDraft task, LocalDate dueDate) {
        jdbcTemplate.update(
                """
                insert into plan_task (career_plan_id, title, description, due_date, priority, source_evidence)
                values (?, ?, ?, ?, ?, ?)
                """,
                careerPlanId,
                task.title(),
                task.persistedDescription(),
                dueDate,
                task.priority(),
                task.sourceEvidence()
        );
    }

    public Optional<Long> findPlanIdByAnalysisReportIdAndUserId(long analysisReportId, long userId) {
        return jdbcTemplate.query(
                "select id from career_plan where analysis_report_id = ? and user_id = ?",
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                analysisReportId,
                userId
        ).stream().findFirst();
    }

    public Optional<CareerPlan> findPlanByIdAndUserId(long planId, long userId) {
        return jdbcTemplate.query(
                "select id, user_id, analysis_report_id, title, summary, duration_days, status, created_at, updated_at "
                        + "from career_plan where id = ? and user_id = ?",
                (resultSet, rowNumber) -> mapPlan(resultSet),
                planId,
                userId
        ).stream().findFirst();
    }

    public List<PlanTask> findTasksByPlanIdAndUserId(long planId, long userId) {
        return jdbcTemplate.query(
                """
                select task.id, task.career_plan_id, task.title, task.description, task.status,
                       task.due_date, task.priority, task.source_evidence, task.completed_at,
                       task.archived_at, task.created_at, task.updated_at
                from plan_task task
                join career_plan plan on plan.id = task.career_plan_id
                where task.career_plan_id = ? and plan.user_id = ? and task.archived_at is null
                order by task.due_date asc, task.id asc
                """,
                (resultSet, rowNumber) -> mapTask(resultSet),
                planId,
                userId
        );
    }

    public Optional<PlanTask> findTaskByIdAndPlanIdAndUserId(long taskId, long planId, long userId) {
        return jdbcTemplate.query(
                """
                select task.id, task.career_plan_id, task.title, task.description, task.status,
                       task.due_date, task.priority, task.source_evidence, task.completed_at,
                       task.archived_at, task.created_at, task.updated_at
                from plan_task task
                join career_plan plan on plan.id = task.career_plan_id
                where task.id = ? and task.career_plan_id = ? and plan.user_id = ? and task.archived_at is null
                """,
                (resultSet, rowNumber) -> mapTask(resultSet),
                taskId,
                planId,
                userId
        ).stream().findFirst();
    }

    public PlanTask updateTask(long taskId, long planId, long userId, String status, LocalDate dueDate,
                               boolean setCompletedAt, boolean clearCompletedAt) {
        return jdbcTemplate.queryForObject(
                """
                update plan_task task
                set status = ?,
                    due_date = ?,
                    completed_at = case when ? then current_timestamp when ? then null else task.completed_at end,
                    updated_at = current_timestamp
                from career_plan plan
                where task.id = ? and task.career_plan_id = ?
                  and plan.id = task.career_plan_id and plan.user_id = ? and task.archived_at is null
                returning task.id, task.career_plan_id, task.title, task.description, task.status,
                          task.due_date, task.priority, task.source_evidence, task.completed_at,
                          task.archived_at, task.created_at, task.updated_at
                """,
                (resultSet, rowNumber) -> mapTask(resultSet),
                status,
                dueDate,
                setCompletedAt,
                clearCompletedAt,
                taskId,
                planId,
                userId
        );
    }

    public int archiveRemainingTasks(long planId, long userId) {
        return jdbcTemplate.update(
                """
                update plan_task task
                set archived_at = current_timestamp, updated_at = current_timestamp
                from career_plan plan
                where task.career_plan_id = ? and plan.id = task.career_plan_id and plan.user_id = ?
                  and task.archived_at is null and task.status = 'TODO'
                """,
                planId,
                userId
        );
    }

    private CareerPlan mapPlan(ResultSet resultSet) throws SQLException {
        return new CareerPlan(
                resultSet.getLong("id"),
                resultSet.getLong("user_id"),
                resultSet.getLong("analysis_report_id"),
                resultSet.getString("title"),
                resultSet.getString("summary"),
                resultSet.getShort("duration_days"),
                resultSet.getString("status"),
                instantOrNull(resultSet, "created_at"),
                instantOrNull(resultSet, "updated_at")
        );
    }

    private PlanTask mapTask(ResultSet resultSet) throws SQLException {
        return new PlanTask(
                resultSet.getLong("id"),
                resultSet.getLong("career_plan_id"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getString("status"),
                resultSet.getObject("due_date", LocalDate.class),
                resultSet.getString("priority"),
                resultSet.getString("source_evidence"),
                instantOrNull(resultSet, "completed_at"),
                instantOrNull(resultSet, "archived_at"),
                instantOrNull(resultSet, "created_at"),
                instantOrNull(resultSet, "updated_at")
        );
    }

    private Instant instantOrNull(ResultSet resultSet, String column) throws SQLException {
        java.sql.Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
