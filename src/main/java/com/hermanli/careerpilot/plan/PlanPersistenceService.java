package com.hermanli.careerpilot.plan;

import com.hermanli.careerpilot.analysis.AnalysisReportRepository;
import com.hermanli.careerpilot.analysis.MatchReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class PlanPersistenceService {

    private final PlanRepository planRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final Clock clock;

    public PlanPersistenceService(
            PlanRepository planRepository,
            AnalysisReportRepository analysisReportRepository
    ) {
        this.planRepository = planRepository;
        this.analysisReportRepository = analysisReportRepository;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public long savePlanAndCompleteAnalysis(
            long userId,
            long analysisId,
            MatchReport report,
            String reportJson,
            PlanDraft plan
    ) {
        long planId = planRepository.create(userId, analysisId, plan);
        LocalDate startDate = LocalDate.now(clock);
        plan.tasks().forEach(task -> planRepository.createTask(
                planId, task, startDate.plusDays(task.dayOffset() - 1L)
        ));
        if (!analysisReportRepository.markCompleted(analysisId, userId, report, reportJson)) {
            throw new IllegalStateException("The running analysis could not be completed.");
        }
        return planId;
    }

    /**
     * Replaces exactly the TODO tasks captured before plan generation. The model call happens outside this short
     * transaction, so any task that stopped being TODO in the meantime rolls the whole replacement back.
     */
    @Transactional
    public List<PlanTask> replaceRemainingTasks(long userId, long planId, Set<Long> expectedTodoTaskIds,
                                                List<PlanTaskDraft> tasks) {
        int archived = planRepository.archiveTodoTasks(planId, userId, expectedTodoTaskIds);
        if (archived != expectedTodoTaskIds.size()) {
            throw new InvalidPlanStateException("The remaining tasks changed while the plan was being regenerated.");
        }
        LocalDate startDate = LocalDate.now(clock);
        tasks.forEach(task -> planRepository.createTask(planId, task, startDate.plusDays(task.dayOffset() - 1L)));
        List<PlanTask> currentTasks = planRepository.findTasksByPlanIdAndUserId(planId, userId);
        if (currentTasks.size() > PlanGenerationService.MAXIMUM_ACTIVE_TASKS
                || currentTasks.stream().anyMatch(task -> expectedTodoTaskIds.contains(task.id()))) {
            throw new InvalidPlanStateException("The regenerated plan exceeds the current-task limit.");
        }
        return currentTasks;
    }
}
