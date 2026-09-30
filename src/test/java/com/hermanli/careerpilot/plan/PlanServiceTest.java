package com.hermanli.careerpilot.plan;

import com.hermanli.careerpilot.analysis.AnalysisReportRepository;
import com.hermanli.careerpilot.analysis.AnalysisReportService;
import com.hermanli.careerpilot.analysis.AnalysisReportView;
import com.hermanli.careerpilot.analysis.AnalysisStatus;
import com.hermanli.careerpilot.analysis.MatchReport;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.ai.AiAvailability;
import com.hermanli.careerpilot.ai.AiUnavailableException;
import com.hermanli.careerpilot.documents.JobDescriptionRepository;
import com.hermanli.careerpilot.publicrag.PublicRagGuardProperties;
import com.hermanli.careerpilot.publicrag.PublicRagGuardService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlanServiceTest {

    private final PlanRepository planRepository = mock(PlanRepository.class);
    private final AnalysisReportService analysisReportService = mock(AnalysisReportService.class);
    private final JobDescriptionRepository jobDescriptionRepository = mock(JobDescriptionRepository.class);
    private final PlanGenerationService planGenerationService = mock(PlanGenerationService.class);
    private final PlanPersistenceService planPersistenceService =
            new PlanPersistenceService(planRepository, mock(AnalysisReportRepository.class));
    private final PlanService planService = new PlanService(
            planRepository, analysisReportService, jobDescriptionRepository, planGenerationService,
            planPersistenceService, new AiAvailability(true), emptyGuardProvider(), new PublicRagGuardProperties(), false
    );

    @Test
    void completesTaskAndSetsCompletedTimestamp() {
        PlanTask existing = task(3L, "IN_PROGRESS", null, null, LocalDate.of(2026, 9, 4));
        PlanTask updated = task(3L, "COMPLETED", Instant.parse("2026-09-01T12:00:00Z"), null, LocalDate.of(2026, 9, 4));
        when(planRepository.findTaskByIdAndPlanIdAndUserId(3L, 2L, 1L)).thenReturn(Optional.of(existing));
        when(planRepository.updateTask(eq(3L), eq(2L), eq(1L), eq("COMPLETED"),
                eq(LocalDate.of(2026, 9, 4)), eq(true), eq(false))).thenReturn(updated);

        assertEquals(updated, planService.updateTask(1L, 2L, 3L, new UpdatePlanTaskRequest("COMPLETED", null)));
    }

    @Test
    void reopensCompletedTaskAndClearsCompletedTimestampWhileUpdatingDueDate() {
        PlanTask existing = task(3L, "COMPLETED", Instant.parse("2026-09-01T12:00:00Z"), null, LocalDate.of(2026, 9, 4));
        PlanTask updated = task(3L, "TODO", null, null, LocalDate.of(2026, 9, 8));
        when(planRepository.findTaskByIdAndPlanIdAndUserId(3L, 2L, 1L)).thenReturn(Optional.of(existing));
        when(planRepository.updateTask(3L, 2L, 1L, "TODO", LocalDate.of(2026, 9, 8), false, true))
                .thenReturn(updated);

        assertEquals(updated, planService.updateTask(1L, 2L, 3L, new UpdatePlanTaskRequest("TODO", LocalDate.of(2026, 9, 8))));
        verify(planRepository).updateTask(3L, 2L, 1L, "TODO", LocalDate.of(2026, 9, 8), false, true);
    }

    @Test
    void replacesFourteenCurrentTodoTasksWithoutExceedingEightCurrentTasks() {
        CareerPlan plan = new CareerPlan(2L, 1L, 9L, "Plan", "Summary", (short) 14, "ACTIVE", Instant.EPOCH, Instant.EPOCH);
        PlanTask completed = task(3L, "COMPLETED", Instant.EPOCH, null, LocalDate.of(2026, 9, 1));
        List<PlanTask> oldTodoTasks = new java.util.ArrayList<>();
        for (long id = 20L; id < 34L; id++) {
            oldTodoTasks.add(task(id, "TODO", null, null, LocalDate.of(2026, 9, 2)));
        }
        List<PlanTask> existingTasks = new ArrayList<>();
        existingTasks.add(completed);
        existingTasks.addAll(oldTodoTasks);
        MatchReport report = new MatchReport(50, List.of("Programming"), List.of(), List.of("Cloud"), List.of(), List.of("No cloud evidence"), List.of("Add cloud evidence"));
        PlanTaskDraft replacementTask = new PlanTaskDraft(
                "Cloud practice", "Complete a cloud exercise.", 1, "HIGH", "Cloud",
                "CLOUD_COMPUTING", "CONCEPT_LEARNING", "One page of cloud notes"
        );
        PlanDraft replacement = new PlanDraft("Plan", "Updated summary", Collections.nCopies(7, replacementTask));
        List<PlanTask> refreshed = List.of(
                completed,
                task(6L, "TODO", null, null, LocalDate.now()),
                task(7L, "TODO", null, null, LocalDate.now()),
                task(8L, "TODO", null, null, LocalDate.now()),
                task(9L, "TODO", null, null, LocalDate.now()),
                task(10L, "TODO", null, null, LocalDate.now()),
                task(11L, "TODO", null, null, LocalDate.now()),
                task(12L, "TODO", null, null, LocalDate.now())
        );
        when(planRepository.findPlanByIdAndUserId(2L, 1L)).thenReturn(Optional.of(plan));
        when(planRepository.findTasksByPlanIdAndUserId(2L, 1L)).thenReturn(existingTasks, refreshed);
        when(analysisReportService.get(1L, 9L)).thenReturn(new AnalysisReportView(9L, 11L, 12L, AnalysisStatus.COMPLETED,
                report, 2L, null, null, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(12L, 1L)).thenReturn(Optional.of("{\"requiredSkills\":[\"Cloud\"]}"));
        stubGeneration(report, existingTasks, 7, replacement);
        when(planRepository.archiveTodoTasks(2L, 1L, oldTodoTasks.stream().map(PlanTask::id)
                .collect(java.util.stream.Collectors.toSet()))).thenReturn(14);

        assertEquals(refreshed, planService.regenerateRemaining(1L, 2L));
        assertEquals(8, refreshed.size());
        assertEquals(0, refreshed.stream().filter(task -> oldTodoTasks.stream().anyMatch(old -> old.id() == task.id())).count());
        verify(planRepository).archiveTodoTasks(2L, 1L, oldTodoTasks.stream().map(PlanTask::id)
                .collect(java.util.stream.Collectors.toSet()));
        verify(planRepository, times(7)).createTask(eq(2L), eq(replacementTask), any(LocalDate.class));
    }

    @Test
    void callsTheModelBeforeArchivingOnlyTheSnapshotTodoTasks() {
        CareerPlan plan = new CareerPlan(2L, 1L, 9L, "Plan", "Summary", (short) 14, "ACTIVE", Instant.EPOCH, Instant.EPOCH);
        PlanTask todoA = task(3L, "TODO", null, null, LocalDate.now());
        PlanTask todoB = task(4L, "TODO", null, null, LocalDate.now());
        PlanTask completedC = task(5L, "COMPLETED", Instant.EPOCH, null, LocalDate.now());
        MatchReport report = new MatchReport(50, List.of(), List.of(), List.of("Cloud"), List.of(), List.of(), List.of());
        PlanTaskDraft replacementTask = new PlanTaskDraft(
                "Cloud task", "Verify cloud evidence.", 1, "HIGH", "Cloud",
                "CLOUD_COMPUTING", "EVIDENCE_VERIFICATION", "A yes/no conclusion"
        );
        List<PlanTask> refreshed = List.of(completedC, task(6L, "TODO", null, null, LocalDate.now()));
        when(planRepository.findPlanByIdAndUserId(2L, 1L)).thenReturn(Optional.of(plan));
        when(planRepository.findTasksByPlanIdAndUserId(2L, 1L)).thenReturn(List.of(todoA, todoB, completedC), refreshed);
        when(analysisReportService.get(1L, 9L)).thenReturn(new AnalysisReportView(9L, 11L, 12L, AnalysisStatus.COMPLETED,
                report, 2L, null, null, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(12L, 1L)).thenReturn(Optional.of("{}"));
        PlanGenerationService.PreparedPlan prepared = stubGeneration(report, List.of(todoA, todoB, completedC), 7,
                new PlanDraft("Plan", "Summary", List.of(replacementTask)));
        when(planRepository.archiveTodoTasks(2L, 1L, java.util.Set.of(3L, 4L))).thenReturn(2);

        assertEquals(refreshed, planService.regenerateRemaining(1L, 2L));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(planGenerationService, planRepository);
        order.verify(planGenerationService).generatePrepared(prepared);
        order.verify(planRepository).archiveTodoTasks(2L, 1L, java.util.Set.of(3L, 4L));
        order.verify(planRepository).createTask(eq(2L), eq(replacementTask), any(LocalDate.class));
    }

    @Test
    void keepsTheModelCallOutsideAnyTransactionAndTheReplacementInsideOne() throws Exception {
        for (java.lang.reflect.Method method : PlanService.class.getMethods()) {
            if (method.getName().equals("regenerateRemaining")) {
                assertEquals(null, method.getAnnotation(org.springframework.transaction.annotation.Transactional.class));
            }
        }
        assertEquals(null, PlanService.class
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class));
        org.junit.jupiter.api.Assertions.assertNotNull(PlanPersistenceService.class.getMethod(
                "replaceRemainingTasks", long.class, long.class, java.util.Set.class, List.class
        ).getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }

    @Test
    void rejectsRegenerationWhenNoOpenTaskRemains() {
        when(planRepository.findPlanByIdAndUserId(2L, 1L)).thenReturn(Optional.of(
                new CareerPlan(2L, 1L, 9L, "Plan", "Summary", (short) 14, "ACTIVE", Instant.EPOCH, Instant.EPOCH)
        ));
        when(planRepository.findTasksByPlanIdAndUserId(2L, 1L)).thenReturn(List.of(task(3L, "COMPLETED", Instant.EPOCH, null, LocalDate.now())));

        assertThrows(InvalidPlanStateException.class, () -> planService.regenerateRemaining(1L, 2L));
    }

    @Test
    void rollsBackWhenNotEveryExpectedTodoWasArchived() {
        CareerPlan plan = new CareerPlan(2L, 1L, 9L, "Plan", "Summary", (short) 14, "ACTIVE", Instant.EPOCH, Instant.EPOCH);
        PlanTask firstTodo = task(3L, "TODO", null, null, LocalDate.now());
        PlanTask secondTodo = task(4L, "TODO", null, null, LocalDate.now());
        MatchReport report = new MatchReport(50, List.of(), List.of(), List.of("Cloud"), List.of(), List.of(), List.of());
        PlanDraft replacement = new PlanDraft("Plan", "Summary", List.of(new PlanTaskDraft(
                "Cloud task", "Verify cloud evidence.", 1, "HIGH", "Cloud",
                "CLOUD_COMPUTING", "EVIDENCE_VERIFICATION", "A yes/no conclusion"
        )));
        when(planRepository.findPlanByIdAndUserId(2L, 1L)).thenReturn(Optional.of(plan));
        when(planRepository.findTasksByPlanIdAndUserId(2L, 1L)).thenReturn(List.of(firstTodo, secondTodo));
        when(analysisReportService.get(1L, 9L)).thenReturn(new AnalysisReportView(9L, 11L, 12L, AnalysisStatus.COMPLETED,
                report, 2L, null, null, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(12L, 1L)).thenReturn(Optional.of("{}"));
        stubGeneration(report, List.of(firstTodo, secondTodo), 8, replacement);
        when(planRepository.archiveTodoTasks(2L, 1L, java.util.Set.of(3L, 4L))).thenReturn(1);

        assertThrows(InvalidPlanStateException.class, () -> planService.regenerateRemaining(1L, 2L));
        verify(planRepository, org.mockito.Mockito.never()).createTask(anyLong(), any(PlanTaskDraft.class), any(LocalDate.class));
    }

    @Test
    void rejectsSuccessfulReturnWhenFinalCurrentTaskCountExceedsEight() {
        CareerPlan plan = new CareerPlan(2L, 1L, 9L, "Plan", "Summary", (short) 14, "ACTIVE", Instant.EPOCH, Instant.EPOCH);
        PlanTask todo = task(3L, "TODO", null, null, LocalDate.now());
        MatchReport report = new MatchReport(50, List.of(), List.of(), List.of("Cloud"), List.of(), List.of(), List.of());
        PlanTaskDraft replacementTask = new PlanTaskDraft(
                "Cloud task", "Verify cloud evidence.", 1, "HIGH", "Cloud",
                "CLOUD_COMPUTING", "EVIDENCE_VERIFICATION", "A yes/no conclusion"
        );
        PlanDraft replacement = new PlanDraft("Plan", "Summary", List.of(replacementTask));
        List<PlanTask> invalidCurrentTasks = new ArrayList<>();
        for (long id = 10L; id < 19L; id++) {
            invalidCurrentTasks.add(task(id, "TODO", null, null, LocalDate.now()));
        }
        when(planRepository.findPlanByIdAndUserId(2L, 1L)).thenReturn(Optional.of(plan));
        when(planRepository.findTasksByPlanIdAndUserId(2L, 1L)).thenReturn(List.of(todo), invalidCurrentTasks);
        when(analysisReportService.get(1L, 9L)).thenReturn(new AnalysisReportView(9L, 11L, 12L, AnalysisStatus.COMPLETED,
                report, 2L, null, null, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(12L, 1L)).thenReturn(Optional.of("{}"));
        stubGeneration(report, List.of(todo), 8, replacement);
        when(planRepository.archiveTodoTasks(2L, 1L, java.util.Set.of(3L))).thenReturn(1);

        assertThrows(InvalidPlanStateException.class, () -> planService.regenerateRemaining(1L, 2L));
    }

    @Test
    void preservesMoreThanEightProtectedTasksAndRejectsRegeneration() {
        CareerPlan plan = new CareerPlan(2L, 1L, 9L, "Plan", "Summary", (short) 14, "ACTIVE", Instant.EPOCH, Instant.EPOCH);
        List<PlanTask> existingTasks = new ArrayList<>();
        for (long id = 1L; id <= 9L; id++) {
            existingTasks.add(task(id, "COMPLETED", Instant.EPOCH, null, LocalDate.now()));
        }
        existingTasks.add(task(10L, "TODO", null, null, LocalDate.now()));
        when(planRepository.findPlanByIdAndUserId(2L, 1L)).thenReturn(Optional.of(plan));
        when(planRepository.findTasksByPlanIdAndUserId(2L, 1L)).thenReturn(existingTasks);

        assertThrows(InvalidPlanStateException.class, () -> planService.regenerateRemaining(1L, 2L));
        verify(planRepository, org.mockito.Mockito.never()).archiveTodoTasks(anyLong(), anyLong(), any());
    }

    @Test
    void generatesTasksForAnExistingEmptyPlan() {
        CareerPlan plan = new CareerPlan(2L, 1L, 9L, "Plan", "Summary", (short) 14, "ACTIVE", Instant.EPOCH, Instant.EPOCH);
        MatchReport report = new MatchReport(50, List.of("Programming"), List.of(), List.of("Cloud"), List.of(), List.of(), List.of("Cloud"));
        PlanDraft generated = new PlanDraft("Plan", "Summary", List.of(
                new PlanTaskDraft(
                        "Cloud practice", "Complete a cloud exercise.", 1, "HIGH", "Cloud",
                        "CLOUD_COMPUTING", "CONCEPT_LEARNING", "One page of cloud notes"
                )
        ));
        List<PlanTask> refreshed = List.of(task(6L, "TODO", null, null, LocalDate.now()));
        when(planRepository.findPlanByIdAndUserId(2L, 1L)).thenReturn(Optional.of(plan));
        when(planRepository.findTasksByPlanIdAndUserId(2L, 1L)).thenReturn(List.of(), refreshed);
        when(analysisReportService.get(1L, 9L)).thenReturn(new AnalysisReportView(9L, 11L, 12L, AnalysisStatus.COMPLETED,
                report, 2L, null, null, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(12L, 1L)).thenReturn(Optional.of("{\"requiredSkills\":[\"Cloud\"]}"));
        stubGeneration(report, List.of(), 8, generated);

        assertEquals(refreshed, planService.regenerateRemaining(1L, 2L));
        verify(planRepository).archiveTodoTasks(2L, 1L, java.util.Set.of());
        verify(planRepository).createTask(eq(2L), eq(generated.tasks().getFirst()), any(LocalDate.class));
    }

    @Test
    void hidesTaskThatDoesNotBelongToTheCurrentUser() {
        when(planRepository.findTaskByIdAndPlanIdAndUserId(3L, 2L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> planService.updateTask(1L, 2L, 3L, new UpdatePlanTaskRequest("TODO", null)));
    }

    @Test
    void publicRegenerationChecksOwnershipBeforeIdentityOrGuard() {
        PublicRagGuardService guard = mock(PublicRagGuardService.class);
        when(planRepository.findPlanByIdAndUserId(2L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> publicService(guard).regenerateRemaining(1L, 2L, null));

        verifyNoInteractions(guard, planGenerationService);
    }

    @Test
    void publicRegenerationRequiresIdentityAndGuardWithoutReservingQuota() {
        PublicRagGuardService guard = mock(PublicRagGuardService.class);
        stubRegenerablePlan();

        assertThrows(com.hermanli.careerpilot.identity.AuthenticationRequiredException.class,
                () -> publicService(guard).regenerateRemaining(1L, 2L, " "));
        assertThrows(AiUnavailableException.class, () -> publicService(null).regenerateRemaining(1L, 2L, "subject-a"));

        verifyNoInteractions(guard, planGenerationService);
        verify(planRepository, org.mockito.Mockito.never()).archiveTodoTasks(anyLong(), anyLong(), any());
    }

    @Test
    void publicArchiveOnlyRegenerationMakesNoModelCallAndReservesNothing() {
        PublicRagGuardService guard = mock(PublicRagGuardService.class);
        List<PlanTask> existingTasks = new ArrayList<>();
        for (long id = 1L; id <= 8L; id++) {
            existingTasks.add(task(id, "COMPLETED", Instant.EPOCH, null, LocalDate.now()));
        }
        existingTasks.add(task(9L, "TODO", null, null, LocalDate.now()));
        List<PlanTask> refreshed = existingTasks.subList(0, 8);
        when(planRepository.findPlanByIdAndUserId(2L, 1L)).thenReturn(Optional.of(plan()));
        when(planRepository.findTasksByPlanIdAndUserId(2L, 1L)).thenReturn(existingTasks, refreshed);
        when(planRepository.archiveTodoTasks(2L, 1L, java.util.Set.of(9L))).thenReturn(1);

        assertEquals(refreshed, publicService(guard).regenerateRemaining(1L, 2L, null));

        verifyNoInteractions(guard, planGenerationService);
    }

    @Test
    void publicRegenerationWithNoGenerableGapReservesNothing() {
        PublicRagGuardService guard = mock(PublicRagGuardService.class);
        MatchReport report = stubRegenerablePlan();
        when(planGenerationService.prepareRemaining(eq(report), any(String.class), eq(List.of(todo())), eq(8)))
                .thenThrow(new PlanGenerationService.InvalidPlanException());

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> publicService(guard).regenerateRemaining(1L, 2L, "subject-a"));

        verifyNoInteractions(guard);
        verify(planGenerationService, org.mockito.Mockito.never()).generatePrepared(any());
    }

    @Test
    void publicRegenerationReservesOneModelCallAndReleasesItBeforePersisting() {
        PublicRagGuardService guard = mock(PublicRagGuardService.class);
        PublicRagGuardService.GuardPermit permit = mock(PublicRagGuardService.GuardPermit.class);
        when(guard.acquireWorkflow(eq("subject-a"), any())).thenReturn(permit);
        MatchReport report = stubRegenerablePlan();
        PlanTaskDraft replacementTask = new PlanTaskDraft(
                "Cloud task", "Verify cloud evidence.", 1, "HIGH", "Cloud",
                "CLOUD_COMPUTING", "EVIDENCE_VERIFICATION", "A yes/no conclusion"
        );
        PlanGenerationService.PreparedPlan prepared = stubGeneration(report, List.of(todo()), 8,
                new PlanDraft("Plan", "Summary", List.of(replacementTask)));
        List<String> promptParts = List.of("{\"gaps\":[]}", "[]");
        when(prepared.guardInputParts()).thenReturn(promptParts);
        when(planRepository.archiveTodoTasks(2L, 1L, java.util.Set.of(3L))).thenReturn(1);

        publicService(guard).regenerateRemaining(1L, 2L, "subject-a");

        verify(guard, times(1)).acquireWorkflow("subject-a", List.of(new PublicRagGuardService.ModelCallBudget(
                PublicRagGuardService.estimateInputTokens(promptParts), 1_600)));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(guard, planGenerationService, permit, planRepository);
        order.verify(guard).acquireWorkflow(eq("subject-a"), any());
        order.verify(planGenerationService).generatePrepared(prepared);
        order.verify(permit).close();
        order.verify(planRepository).archiveTodoTasks(2L, 1L, java.util.Set.of(3L));
        order.verify(planRepository).createTask(eq(2L), eq(replacementTask), any(LocalDate.class));
    }

    @Test
    void publicGuardRejectionLeavesTheModelAndTasksUntouched() {
        PublicRagGuardService guard = mock(PublicRagGuardService.class);
        com.hermanli.careerpilot.publicrag.PublicRagGuardRejectedException userLimit =
                mock(com.hermanli.careerpilot.publicrag.PublicRagGuardRejectedException.class);
        when(guard.acquireWorkflow(eq("subject-a"), any())).thenThrow(userLimit);
        MatchReport report = stubRegenerablePlan();
        stubGeneration(report, List.of(todo()), 8, new PlanDraft("Plan", "Summary", List.of()));

        assertThrows(com.hermanli.careerpilot.publicrag.PublicRagGuardRejectedException.class,
                () -> publicService(guard).regenerateRemaining(1L, 2L, "subject-a"));

        verify(planGenerationService, org.mockito.Mockito.never()).generatePrepared(any());
        verify(planRepository, org.mockito.Mockito.never()).archiveTodoTasks(anyLong(), anyLong(), any());
        verify(planRepository, org.mockito.Mockito.never()).createTask(anyLong(), any(PlanTaskDraft.class), any(LocalDate.class));
    }

    @Test
    void publicRegenerationReleasesThePermitWhenTheModelOutputIsRejected() {
        PublicRagGuardService guard = mock(PublicRagGuardService.class);
        PublicRagGuardService.GuardPermit permit = mock(PublicRagGuardService.GuardPermit.class);
        when(guard.acquireWorkflow(eq("subject-a"), any())).thenReturn(permit);
        MatchReport report = stubRegenerablePlan();
        PlanGenerationService.PreparedPlan prepared = mock(PlanGenerationService.PreparedPlan.class);
        when(planGenerationService.prepareRemaining(eq(report), any(String.class), eq(List.of(todo())), eq(8)))
                .thenReturn(prepared);
        when(planGenerationService.generatePrepared(prepared)).thenThrow(new PlanGenerationService.InvalidPlanException());

        assertThrows(PlanGenerationService.InvalidPlanException.class,
                () -> publicService(guard).regenerateRemaining(1L, 2L, "subject-a"));

        verify(permit).close();
        verify(planRepository, org.mockito.Mockito.never()).archiveTodoTasks(anyLong(), anyLong(), any());
    }

    private PlanService publicService(PublicRagGuardService guard) {
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<PublicRagGuardService> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(guard);
        return new PlanService(planRepository, analysisReportService, jobDescriptionRepository, planGenerationService,
                planPersistenceService, new AiAvailability(true, true), provider, new PublicRagGuardProperties(), true);
    }

    @SuppressWarnings("unchecked")
    private static org.springframework.beans.factory.ObjectProvider<PublicRagGuardService> emptyGuardProvider() {
        return mock(org.springframework.beans.factory.ObjectProvider.class);
    }

    private PlanGenerationService.PreparedPlan stubGeneration(MatchReport report, List<PlanTask> priorTasks,
                                                              int taskLimit, PlanDraft draft) {
        PlanGenerationService.PreparedPlan prepared = mock(PlanGenerationService.PreparedPlan.class);
        when(planGenerationService.prepareRemaining(eq(report), any(String.class), eq(priorTasks), eq(taskLimit)))
                .thenReturn(prepared);
        when(planGenerationService.generatePrepared(prepared)).thenReturn(draft);
        return prepared;
    }

    private MatchReport stubRegenerablePlan() {
        MatchReport report = new MatchReport(50, List.of(), List.of(), List.of("Cloud"), List.of(), List.of(), List.of());
        when(planRepository.findPlanByIdAndUserId(2L, 1L)).thenReturn(Optional.of(plan()));
        when(planRepository.findTasksByPlanIdAndUserId(2L, 1L)).thenReturn(List.of(todo()),
                List.of(task(6L, "TODO", null, null, LocalDate.of(2026, 9, 2))));
        when(analysisReportService.get(1L, 9L)).thenReturn(new AnalysisReportView(9L, 11L, 12L, AnalysisStatus.COMPLETED,
                report, 2L, null, null, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(12L, 1L)).thenReturn(Optional.of("{}"));
        return report;
    }

    private CareerPlan plan() {
        return new CareerPlan(2L, 1L, 9L, "Plan", "Summary", (short) 14, "ACTIVE", Instant.EPOCH, Instant.EPOCH);
    }

    private PlanTask todo() {
        return task(3L, "TODO", null, null, LocalDate.of(2026, 9, 2));
    }

    private PlanTask task(long id, String status, Instant completedAt, Instant archivedAt, LocalDate dueDate) {
        return new PlanTask(id, 2L, "Practice Java", "Complete an exercise", status, dueDate, "HIGH",
                "Cloud", completedAt, archivedAt, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
    }
}
