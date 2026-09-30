package com.hermanli.careerpilot.plan;

import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.ai.AiAvailability;
import com.hermanli.careerpilot.ai.AiUnavailableException;
import com.hermanli.careerpilot.analysis.AnalysisReportService;
import com.hermanli.careerpilot.analysis.AnalysisReportView;
import com.hermanli.careerpilot.documents.JobDescriptionRepository;
import com.hermanli.careerpilot.identity.AuthenticationRequiredException;
import com.hermanli.careerpilot.publicrag.PublicRagGuardProperties;
import com.hermanli.careerpilot.publicrag.PublicRagGuardService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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
    private final PlanPersistenceService planPersistenceService;
    private final AiAvailability aiAvailability;
    private final ObjectProvider<PublicRagGuardService> guardProvider;
    private final PublicRagGuardProperties guardProperties;
    private final boolean clerkApplicationAuthenticationEnabled;

    public PlanService(
            PlanRepository planRepository,
            AnalysisReportService analysisReportService,
            JobDescriptionRepository jobDescriptionRepository,
            PlanGenerationService planGenerationService,
            PlanPersistenceService planPersistenceService,
            AiAvailability aiAvailability,
            ObjectProvider<PublicRagGuardService> guardProvider,
            PublicRagGuardProperties guardProperties,
            @Value("${careerpilot.auth.clerk-application-enabled:false}")
            boolean clerkApplicationAuthenticationEnabled
    ) {
        this.planRepository = planRepository;
        this.analysisReportService = analysisReportService;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.planGenerationService = planGenerationService;
        this.planPersistenceService = planPersistenceService;
        this.aiAvailability = aiAvailability;
        this.guardProvider = guardProvider;
        this.guardProperties = guardProperties;
        this.clerkApplicationAuthenticationEnabled = clerkApplicationAuthenticationEnabled;
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

    // Deliberately not transactional: the model call must not hold a database transaction or connection. The task
    // snapshot taken here is enforced by the short replacement transaction in PlanPersistenceService.
    public List<PlanTask> regenerateRemaining(long userId, long planId) {
        return regenerateRemaining(userId, planId, null);
    }

    public List<PlanTask> regenerateRemaining(long userId, long planId, String clerkSubject) {
        CareerPlan plan = get(userId, planId);
        aiAvailability.requireEnabled(AiAvailability.Operation.GUARDED_PLAN_REGENERATION);
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
            // Archiving without generation makes no model call, so it never touches the guard.
            return planPersistenceService.replaceRemainingTasks(userId, planId, replacedTaskIds, List.of());
        }
        PublicRagGuardService guard = clerkApplicationAuthenticationEnabled ? requireGuard(clerkSubject) : null;
        AnalysisReportView analysis = analysisReportService.get(userId, plan.analysisReportId());
        if (analysis.report() == null) {
            throw new InvalidPlanStateException("The match report is not available for this plan.");
        }
        String jobDescriptionJson = jobDescriptionRepository
                .findCompletedParsedJsonByIdAndUserId(analysis.jobDescriptionId(), userId)
                .orElseThrow(() -> new InvalidPlanStateException("The saved job description is not available for this plan."));
        // Preparing rejects plans with no generable gap before any model call or quota reservation.
        PlanGenerationService.PreparedPlan prepared = planGenerationService.prepareRemaining(
                analysis.report(), jobDescriptionJson, existingTasks, taskLimit
        );
        PlanDraft replacement;
        if (guard == null) {
            replacement = planGenerationService.generatePrepared(prepared);
        } else {
            PublicRagGuardService.ModelCallBudget call = new PublicRagGuardService.ModelCallBudget(
                    PublicRagGuardService.estimateInputTokens(prepared.guardInputParts()),
                    guardProperties.getPlanMaxOutputTokens()
            );
            try (PublicRagGuardService.GuardPermit ignored = guard.acquireWorkflow(clerkSubject, List.of(call))) {
                replacement = planGenerationService.generatePrepared(prepared);
            }
        }
        return planPersistenceService.replaceRemainingTasks(userId, planId, replacedTaskIds, replacement.tasks());
    }

    private PublicRagGuardService requireGuard(String clerkSubject) {
        if (clerkSubject == null || clerkSubject.isBlank()) {
            throw new AuthenticationRequiredException();
        }
        PublicRagGuardService guard = guardProvider.getIfAvailable();
        if (guard == null) {
            throw new AiUnavailableException();
        }
        return guard;
    }

    private boolean isRegenerableTask(PlanTask task) {
        return task.archivedAt() == null && "TODO".equals(task.status());
    }

    private boolean isProtectedTask(PlanTask task) {
        return task.archivedAt() == null && Set.of("COMPLETED", "SKIPPED", "IN_PROGRESS").contains(task.status());
    }
}
