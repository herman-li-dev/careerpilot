package com.hermanli.careerpilot.plan;

import com.hermanli.careerpilot.analysis.AnalysisReportService;
import com.hermanli.careerpilot.analysis.AnalysisReportView;
import com.hermanli.careerpilot.analysis.AnalysisStatus;
import com.hermanli.careerpilot.analysis.MatchReport;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.documents.JobDescriptionRepository;
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
import static org.mockito.Mockito.when;

class PlanServiceTest {

    private final PlanRepository planRepository = mock(PlanRepository.class);
    private final AnalysisReportService analysisReportService = mock(AnalysisReportService.class);
    private final JobDescriptionRepository jobDescriptionRepository = mock(JobDescriptionRepository.class);
    private final PlanGenerationService planGenerationService = mock(PlanGenerationService.class);
    private final PlanService planService = new PlanService(
            planRepository, analysisReportService, jobDescriptionRepository, planGenerationService
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
        when(planGenerationService.generateRemaining(eq(report), any(String.class), eq(existingTasks), eq(7))).thenReturn(replacement);
        when(planRepository.archiveRemainingTasks(2L, 1L)).thenReturn(14);

        assertEquals(refreshed, planService.regenerateRemaining(1L, 2L));
        assertEquals(8, refreshed.size());
        assertEquals(0, refreshed.stream().filter(task -> oldTodoTasks.stream().anyMatch(old -> old.id() == task.id())).count());
        verify(planRepository).archiveRemainingTasks(2L, 1L);
        verify(planRepository, times(7)).createTask(eq(2L), eq(replacementTask), any(LocalDate.class));
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
        when(planGenerationService.generateRemaining(eq(report), any(String.class), eq(List.of(firstTodo, secondTodo)), eq(8)))
                .thenReturn(replacement);
        when(planRepository.archiveRemainingTasks(2L, 1L)).thenReturn(1);

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
        when(planGenerationService.generateRemaining(eq(report), any(String.class), eq(List.of(todo)), eq(8)))
                .thenReturn(replacement);
        when(planRepository.archiveRemainingTasks(2L, 1L)).thenReturn(1);

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
        verify(planRepository, org.mockito.Mockito.never()).archiveRemainingTasks(2L, 1L);
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
        when(planGenerationService.generateRemaining(eq(report), any(String.class), eq(List.of()), eq(8))).thenReturn(generated);

        assertEquals(refreshed, planService.regenerateRemaining(1L, 2L));
        verify(planRepository, org.mockito.Mockito.never()).archiveRemainingTasks(2L, 1L);
        verify(planRepository).createTask(eq(2L), eq(generated.tasks().getFirst()), any(LocalDate.class));
    }

    @Test
    void hidesTaskThatDoesNotBelongToTheCurrentUser() {
        when(planRepository.findTaskByIdAndPlanIdAndUserId(3L, 2L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> planService.updateTask(1L, 2L, 3L, new UpdatePlanTaskRequest("TODO", null)));
    }

    private PlanTask task(long id, String status, Instant completedAt, Instant archivedAt, LocalDate dueDate) {
        return new PlanTask(id, 2L, "Practice Java", "Complete an exercise", status, dueDate, "HIGH",
                "Cloud", completedAt, archivedAt, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
    }
}
