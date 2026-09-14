package com.hermanli.careerpilot.database;

import com.hermanli.careerpilot.plan.PlanRepository;
import com.hermanli.careerpilot.plan.PlanTask;
import com.hermanli.careerpilot.plan.PlanTaskDraft;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@Tag("external")
@Transactional
class CareerPlanSchemaMigrationTest {

    private static final String SYNTHETIC_PASSWORD_HASH =
            "$2a$12$syntheticHashValueForSchemaTestsOnly000000000000000000";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlanRepository planRepository;

    @Test
    void createsOnlyTheDocumentedPlanAndTaskColumns() {
        assertEquals(Set.of(
                "id", "user_id", "analysis_report_id", "title", "summary", "duration_days", "status",
                "created_at", "updated_at"
        ), tableColumns("career_plan"));
        assertEquals(Set.of(
                "id", "career_plan_id", "title", "description", "status", "due_date", "priority",
                "source_evidence", "completed_at", "archived_at", "created_at", "updated_at"
        ), tableColumns("plan_task"));
    }

    @Test
    void enforcesSinglePlanPerReportAndDocumentedLifecycleValues() {
        Inputs inputs = insertInputs("plan-constraints-owner@example.com");
        long planId = insertPlan(inputs.userId(), inputs.analysisReportId());

        assertEquals((short) 14, jdbcTemplate.queryForObject(
                "select duration_days from career_plan where id = ?", Short.class, planId
        ));
        assertThrows(DataIntegrityViolationException.class, () -> insertPlan(inputs.userId(), inputs.analysisReportId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "update career_plan set status = 'UNKNOWN' where id = ?", planId
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "insert into plan_task (career_plan_id, title, description, due_date, priority, source_evidence, status) values (?, ?, ?, ?, ?, ?, ?)",
                planId, "Synthetic task", "Synthetic description", LocalDate.of(2026, 9, 1), "HIGH", "Synthetic evidence", "UNKNOWN"
        ));
    }

    @Test
    void derivesTaskOwnershipThroughThePlanOwner() {
        Inputs owner = insertInputs("plan-owner@example.com");
        Inputs other = insertInputs("plan-other@example.com");
        long planId = insertPlan(owner.userId(), owner.analysisReportId());
        long taskId = jdbcTemplate.queryForObject(
                "insert into plan_task (career_plan_id, title, description, due_date, priority, source_evidence) values (?, ?, ?, ?, ?, ?) returning id",
                Long.class, planId, "Synthetic task", "Synthetic description", LocalDate.of(2026, 9, 1), "MEDIUM", "Synthetic evidence"
        );

        assertEquals(1, ownedTaskCount(taskId, owner.userId()));
        assertEquals(0, ownedTaskCount(taskId, other.userId()));
    }

    @Test
    void createsTheKnownTaskDueDateIndexInColumnOrder() {
        assertEquals(
                "CREATE INDEX ix_plan_task_career_plan_due_date ON public.plan_task USING btree (career_plan_id, due_date)",
                jdbcTemplate.queryForObject(
                        "select indexdef from pg_indexes where schemaname = 'public' and indexname = ?",
                        String.class,
                        "ix_plan_task_career_plan_due_date"
                )
        );
    }

    @Test
    void replacesFourteenLegacyCurrentTodoRowsAndReturnsAtMostEightCurrentTasks() {
        Inputs inputs = insertInputs("plan-regeneration-history@example.com");
        long planId = insertPlan(inputs.userId(), inputs.analysisReportId());
        jdbcTemplate.update(
                "insert into plan_task (career_plan_id, title, description, status, due_date, priority, source_evidence, completed_at) values (?, ?, ?, 'COMPLETED', ?, 'HIGH', ?, current_timestamp)",
                planId, "Protected completed task", "Synthetic completed task", LocalDate.of(2026, 9, 1), "Programming"
        );
        Set<Long> replacedIds = new HashSet<>();
        for (int day = 1; day <= 14; day++) {
            Long id = jdbcTemplate.queryForObject(
                    "insert into plan_task (career_plan_id, title, description, status, due_date, priority, source_evidence) values (?, ?, ?, 'TODO', ?, 'MEDIUM', ?) returning id",
                    Long.class, planId, "Legacy TODO " + day, "Synthetic TODO task", LocalDate.of(2026, 9, 1).plusDays(day), "Cloud Computing"
            );
            replacedIds.add(id);
        }

        assertEquals(15, planRepository.findTasksByPlanIdAndUserId(planId, inputs.userId()).size());
        assertEquals(14, planRepository.archiveRemainingTasks(planId, inputs.userId()));
        for (int day = 1; day <= 7; day++) {
            planRepository.createTask(planId, new PlanTaskDraft(
                    "Replacement " + day,
                    "Complete one synthetic replacement action.",
                    day,
                    "MEDIUM",
                    "Cloud Computing",
                    "CLOUD_COMPUTING",
                    "EVIDENCE_VERIFICATION",
                    "A synthetic verification result"
            ), LocalDate.of(2026, 10, 1).plusDays(day - 1L));
        }

        List<PlanTask> currentTasks = planRepository.findTasksByPlanIdAndUserId(planId, inputs.userId());
        assertEquals(8, currentTasks.size());
        assertEquals(0, currentTasks.stream().filter(task -> replacedIds.contains(task.id())).count());
        assertEquals(14, jdbcTemplate.queryForObject(
                "select count(*) from plan_task where career_plan_id = ? and archived_at is not null",
                Integer.class, planId
        ));
        assertEquals(22, jdbcTemplate.queryForObject(
                "select count(*) from plan_task where career_plan_id = ?",
                Integer.class, planId
        ));
    }

    private Set<String> tableColumns(String tableName) {
        List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_schema = 'public' and table_name = ?",
                String.class,
                tableName
        );
        return new HashSet<>(columns);
    }

    private long insertPlan(long userId, long analysisReportId) {
        return jdbcTemplate.queryForObject(
                "insert into career_plan (user_id, analysis_report_id, title, summary) values (?, ?, ?, ?) returning id",
                Long.class,
                userId,
                analysisReportId,
                "Synthetic plan",
                "Synthetic plan summary"
        );
    }

    private int ownedTaskCount(long taskId, long userId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from plan_task task join career_plan plan on plan.id = task.career_plan_id where task.id = ? and plan.user_id = ?",
                Integer.class,
                taskId,
                userId
        );
    }

    private Inputs insertInputs(String email) {
        long userId = jdbcTemplate.queryForObject(
                "insert into app_user (email, password_hash) values (?, ?) returning id",
                Long.class,
                email,
                SYNTHETIC_PASSWORD_HASH
        );
        long resumeId = jdbcTemplate.queryForObject(
                "insert into resume (user_id, title, raw_text) values (?, ?, ?) returning id",
                Long.class,
                userId, "Synthetic resume", "Synthetic resume text"
        );
        long jobDescriptionId = jdbcTemplate.queryForObject(
                "insert into job_description (user_id, title, raw_text) values (?, ?, ?) returning id",
                Long.class,
                userId, "Synthetic role", "Synthetic job description text"
        );
        long analysisReportId = jdbcTemplate.queryForObject(
                "insert into analysis_report (user_id, resume_id, job_description_id) values (?, ?, ?) returning id",
                Long.class,
                userId, resumeId, jobDescriptionId
        );
        return new Inputs(userId, analysisReportId);
    }

    private record Inputs(long userId, long analysisReportId) {
    }
}
