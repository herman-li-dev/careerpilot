package com.hermanli.careerpilot.plan;

import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.analysis.AnalysisReportService;
import com.hermanli.careerpilot.analysis.AnalysisReportView;
import com.hermanli.careerpilot.documents.JobDescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final AnalysisReportService analysisReportService;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final PlanGenerationService planGenerationService;

    public PlanService(
            PlanRepository planRepository,
            AnalysisReportService analysisReportService,
            JobDescriptionRepository jobDescriptionRepository,
            PlanGenerationService planGenerationService
    ) {
        this.planRepository = planRepository;
        this.analysisReportService = analysisReportService;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.planGenerationService = planGenerationService;
    }

    public CareerPlan get(long userId, long planId) {
        return planRepository.findPlanByIdAndUserId(planId, userId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    public List<PlanTask> listTasks(long userId, long planId) {
        get(userId, planId);
        return planRepository.findTasksByPlanIdAndUserId(planId, userId);
    }

    @Transactional
    public PlanTask updateTask(long userId, long planId, long taskId, UpdatePlanTaskRequest request) {
        PlanTask existing = planRepository.findTaskByIdAndPlanIdAndUserId(taskId, planId, userId)
                .orElseThrow(ResourceNotFoundException::new);
        String nextStatus = request.status() == null ? existing.status() : request.status();
        LocalDate nextDueDate = request.dueDate() == null ? existing.dueDate() : request.dueDate();
        boolean statusChanged = !nextStatus.equals(existing.status());
        return planRepository.updateTask(
                taskId,
                planId,
                userId,
                nextStatus,
                nextDueDate,
                statusChanged && "COMPLETED".equals(nextStatus),
                statusChanged && "COMPLETED".equals(existing.status())
        );
    }

    @Transactional
    public List<PlanTask> regenerateRemaining(long userId, long planId) {
        CareerPlan plan = get(userId, planId);
        List<PlanTask> existingTasks = planRepository.findTasksByPlanIdAndUserId(planId, userId);
        boolean emptyPlan = existingTasks.isEmpty();
        if (!emptyPlan && existingTasks.stream().noneMatch(this::isRegenerableTask)) {
            throw new InvalidPlanStateException("There are no remaining tasks to regenerate.");
        }
        List<PlanTask> regenerableTasks = existingTasks.stream().filter(this::isRegenerableTask).toList();
        Set<Long> replacedTaskIds = regenerableTasks.stream()
                .map(PlanTask::id)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        int protectedTaskCount = (int) existingTasks.stream().filter(this::isProtectedTask).count();
        int taskLimit = PlanGenerationService.MAXIMUM_ACTIVE_TASKS - protectedTaskCount;
        if (taskLimit < 0) {
            throw new InvalidPlanStateException("There is no room for regenerated tasks in this plan.");
        }
        if (!emptyPlan && taskLimit == 0) {
            archiveExpectedTasks(planId, userId, regenerableTasks.size());
            return requireValidCurrentTasks(planId, userId, replacedTaskIds);
        }
        AnalysisReportView analysis = analysisReportService.get(userId, plan.analysisReportId());
        if (analysis.report() == null) {
            throw new InvalidPlanStateException("The match report is not available for this plan.");
        }
        String jobDescriptionJson = jobDescriptionRepository
                .findCompletedParsedJsonByIdAndUserId(analysis.jobDescriptionId(), userId)
                .orElseThrow(() -> new InvalidPlanStateException("The saved job description is not available for this plan."));
        PlanDraft replacement = planGenerationService.generateRemaining(
                analysis.report(), jobDescriptionJson, existingTasks, taskLimit
        );
        if (!emptyPlan) {
            archiveExpectedTasks(planId, userId, regenerableTasks.size());
        }
        LocalDate startDate = LocalDate.now(java.time.Clock.systemUTC());
        replacement.tasks().forEach(task -> planRepository.createTask(
                planId, task, startDate.plusDays(task.dayOffset() - 1L)
        ));
        return requireValidCurrentTasks(planId, userId, replacedTaskIds);
    }

    private void archiveExpectedTasks(long planId, long userId, int expectedCount) {
        int archivedCount = planRepository.archiveRemainingTasks(planId, userId);
        if (archivedCount != expectedCount) {
            throw new InvalidPlanStateException("The remaining tasks changed while the plan was being regenerated.");
        }
    }

    private List<PlanTask> requireValidCurrentTasks(long planId, long userId, Set<Long> replacedTaskIds) {
        List<PlanTask> currentTasks = planRepository.findTasksByPlanIdAndUserId(planId, userId);
        if (currentTasks.size() > PlanGenerationService.MAXIMUM_ACTIVE_TASKS
                || currentTasks.stream().anyMatch(task -> replacedTaskIds.contains(task.id()))) {
            throw new InvalidPlanStateException("The regenerated plan exceeds the current-task limit.");
        }
        return currentTasks;
    }

    private boolean isRegenerableTask(PlanTask task) {
        return task.archivedAt() == null && "TODO".equals(task.status());
    }

    private boolean isProtectedTask(PlanTask task) {
        return task.archivedAt() == null && Set.of("COMPLETED", "SKIPPED", "IN_PROGRESS").contains(task.status());
    }
}
