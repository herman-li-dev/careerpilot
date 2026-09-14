package com.hermanli.careerpilot.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermanli.careerpilot.ai.AiAvailability;
import com.hermanli.careerpilot.ai.AiUnavailableException;
import com.hermanli.careerpilot.documents.JobDescriptionRepository;
import com.hermanli.careerpilot.documents.ResumeRepository;
import com.hermanli.careerpilot.plan.PlanDraft;
import com.hermanli.careerpilot.plan.PlanGenerationService;
import com.hermanli.careerpilot.plan.PlanPersistenceService;
import com.hermanli.careerpilot.plan.PlanTaskDraft;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisReportServiceTest {

    private static final long USER_ID = 7L;
    private static final long RESUME_ID = 11L;
    private static final long JOB_DESCRIPTION_ID = 13L;
    private static final String RESUME_JSON = """
            {"skills":["Java","Spring Boot"],"projects":["Deployment project"]}
            """;
    private static final String JOB_DESCRIPTION_JSON = """
            {"requiredSkills":["Java","Docker"],"responsibilities":["Deploy Java APIs"]}
            """;
    private static final String VALID_REPORT_JSON = """
            {
              "matchScore":72,
              "matchedSkills":["Java"],
              "partialMatches":[],
              "missingSkills":["Docker"],
              "strengths":["Java project"],
              "risks":["Docker requirement"],
              "recommendations":["Add Docker deployment evidence"]
            }
            """;

    private ResumeRepository resumeRepository;
    private JobDescriptionRepository jobDescriptionRepository;
    private AnalysisReportRepository analysisReportRepository;
    private FakeReportGenerator reportGenerator;
    private PlanGenerationService planGenerationService;
    private PlanPersistenceService planPersistenceService;
    private AnalysisReportService service;

    @BeforeEach
    void setUp() {
        resumeRepository = mock(ResumeRepository.class);
        jobDescriptionRepository = mock(JobDescriptionRepository.class);
        analysisReportRepository = mock(AnalysisReportRepository.class);
        reportGenerator = new FakeReportGenerator();
        planGenerationService = mock(PlanGenerationService.class);
        planPersistenceService = mock(PlanPersistenceService.class);
        service = new AnalysisReportService(
                resumeRepository,
                jobDescriptionRepository,
                analysisReportRepository,
                reportGenerator,
                planGenerationService,
                planPersistenceService,
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator(),
                new AiAvailability(true)
        );
        when(resumeRepository.findCompletedParsedJsonByIdAndUserId(RESUME_ID, USER_ID))
                .thenReturn(Optional.of(RESUME_JSON));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(JOB_DESCRIPTION_ID, USER_ID))
                .thenReturn(Optional.of(JOB_DESCRIPTION_JSON));
        when(analysisReportRepository.markRunning(anyLong(), eq(USER_ID))).thenReturn(true);
        when(planGenerationService.generate(any(MatchReport.class), anyString())).thenReturn(
                new PlanDraft(
                        "14-Day Preparation Plan", "Synthetic summary",
                        java.util.List.of(new PlanTaskDraft(
                                "Synthetic task", "Synthetic description", 1, "HIGH", "Docker",
                                "DEVOPS_DELIVERY", "EVIDENCE_VERIFICATION", "A synthetic verification checklist"
                        ))
                )
        );
        doAnswer(invocation -> {
            analysisReportRepository.markCompleted(
                    invocation.getArgument(1), invocation.getArgument(0), invocation.getArgument(2), invocation.getArgument(3)
            );
            return 61L;
        }).when(planPersistenceService).savePlanAndCompleteAnalysis(
                anyLong(), anyLong(), any(MatchReport.class), anyString(), any(PlanDraft.class)
        );
    }

    @Test
    void rejectsOfflineGenerationBeforeCreatingPendingAnalysis() {
        AnalysisReportService offlineService = new AnalysisReportService(
                resumeRepository,
                jobDescriptionRepository,
                analysisReportRepository,
                reportGenerator,
                planGenerationService,
                planPersistenceService,
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator(),
                new AiAvailability(false)
        );

        assertThrows(AiUnavailableException.class,
                () -> offlineService.createPending(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID));

        verify(analysisReportRepository, never()).create(anyLong(), anyLong(), anyLong());
    }

    @Test
    void persistsPendingRunningAndCompletedStatesForValidatedEvidence() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(41L);
        reportGenerator.enqueue(VALID_REPORT_JSON);

        long analysisId = service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        assertEquals(41L, analysisId);
        InOrder order = inOrder(analysisReportRepository);
        order.verify(analysisReportRepository).create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);
        order.verify(analysisReportRepository).markRunning(41L, USER_ID);
        order.verify(analysisReportRepository).markCompleted(eq(41L), eq(USER_ID), any(MatchReport.class), anyString());
        verify(analysisReportRepository, never()).markFailed(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void retriesMalformedOutputOnceThenPersistsSafeFailure() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(42L);
        reportGenerator.enqueue("{\"matchScore\": 101}");
        reportGenerator.enqueue("not json");

        long analysisId = service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        assertEquals(42L, analysisId);
        assertEquals(2, reportGenerator.calls);
        verify(analysisReportRepository).markFailed(
                42L,
                USER_ID,
                "INVALID_REPORT_SCHEMA",
                "The model returned an invalid report structure. Please try again."
        );
    }

    @Test
    void retriesDuplicateReportFieldsAndPersistsOnlyTheValidRetry() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(60L);
        reportGenerator.enqueue(VALID_REPORT_JSON.replace(
                "\"partialMatches\":[],",
                "\"partialMatches\":[],\n  \"partialMatches\":[\"Cloud Computing\"],"
        ));
        reportGenerator.enqueue(VALID_REPORT_JSON);

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        assertEquals(2, reportGenerator.calls);
        verify(planPersistenceService).savePlanAndCompleteAnalysis(
                eq(USER_ID), eq(60L), any(MatchReport.class), anyString(), any(PlanDraft.class)
        );
        verify(analysisReportRepository, never()).markFailed(eq(60L), anyLong(), anyString(), anyString());
    }

    @Test
    void doesNotCompleteAnAnalysisWhenPlanGenerationFails() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(58L);
        reportGenerator.enqueue(VALID_REPORT_JSON);
        when(planGenerationService.generate(any(MatchReport.class), anyString()))
                .thenThrow(new PlanGenerationService.InvalidPlanException());
        when(planGenerationService.generateSafeFallback(any(MatchReport.class), anyString()))
                .thenThrow(new PlanGenerationService.InvalidPlanException());

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        verify(planPersistenceService, never()).savePlanAndCompleteAnalysis(
                anyLong(), anyLong(), any(MatchReport.class), anyString(), any(PlanDraft.class)
        );
        verify(analysisReportRepository).markFailed(
                58L, USER_ID, "PLAN_GENERATION_FAILED", "The preparation plan could not be generated. Please try again."
        );
    }

    @Test
    void completesAnalysisWithSafeFallbackAfterTwoInvalidGeneratedPlans() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(59L);
        reportGenerator.enqueue(VALID_REPORT_JSON);
        when(planGenerationService.generate(any(MatchReport.class), anyString()))
                .thenThrow(new PlanGenerationService.InvalidPlanException());
        PlanDraft fallback = new PlanDraft(
                "14-Day Evidence Verification Plan", "Safe fallback",
                java.util.List.of(new PlanTaskDraft(
                        "Verify Docker evidence", "Verify whether real Docker evidence exists.", 1, "HIGH", "Docker",
                        "DEVOPS_DELIVERY", "EVIDENCE_VERIFICATION", "One verified yes/no conclusion"
                ))
        );
        when(planGenerationService.generateSafeFallback(any(MatchReport.class), anyString())).thenReturn(fallback);

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        verify(planGenerationService, org.mockito.Mockito.times(2))
                .generate(any(MatchReport.class), anyString());
        verify(planPersistenceService).savePlanAndCompleteAnalysis(
                eq(USER_ID), eq(59L), any(MatchReport.class), anyString(), eq(fallback)
        );
        verify(analysisReportRepository, never()).markFailed(eq(59L), anyLong(), anyString(), anyString());
    }

    @Test
    void acceptsAJsonCodeFenceWithoutPersistingTheModelResponse() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(45L);
        reportGenerator.enqueue("```json\n" + VALID_REPORT_JSON + "\n```");

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        verify(analysisReportRepository).markCompleted(eq(45L), eq(USER_ID), any(MatchReport.class), anyString());
        verify(analysisReportRepository, never()).markFailed(eq(45L), anyLong(), anyString(), anyString());
    }

    @Test
    void canonicalizesAnInventedToolClaimWithoutPersistingTheInventedEvidence() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(43L);
        String inventedSkillReport = VALID_REPORT_JSON.replace("[\"Java\"]", "[\"Python\"]");
        reportGenerator.enqueue(inventedSkillReport);

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        ArgumentCaptor<MatchReport> reportCaptor = ArgumentCaptor.forClass(MatchReport.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(analysisReportRepository).markCompleted(eq(43L), eq(USER_ID), reportCaptor.capture(), jsonCaptor.capture());
        assertEquals(java.util.List.of("Programming"), reportCaptor.getValue().matchedSkills());
        assertEquals(java.util.List.of("DevOps and Software Delivery"), reportCaptor.getValue().partialMatches());
        assertEquals(java.util.List.of(), reportCaptor.getValue().missingSkills());
        assertEquals(75, reportCaptor.getValue().matchScore());
        assertEquals(false, jsonCaptor.getValue().contains("Python"));
        verify(analysisReportRepository, never()).markFailed(eq(43L), anyLong(), anyString(), anyString());
    }

    @Test
    void demotesAJobGroundedButUnsupportedMatchedSkillToMissing() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(55L);
        when(resumeRepository.findCompletedParsedJsonByIdAndUserId(RESUME_ID, USER_ID))
                .thenReturn(Optional.of("{\"skills\":[\"Java\"]}"));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(JOB_DESCRIPTION_ID, USER_ID))
                .thenReturn(Optional.of("{\"requiredSkills\":[\"Creative thinking\"]}"));
        reportGenerator.enqueue("""
                {"matchScore":100,"matchedSkills":["Creative thinking"],"partialMatches":[],"missingSkills":[],"strengths":[],"risks":[],"recommendations":[]}
                """);

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        ArgumentCaptor<MatchReport> reportCaptor = ArgumentCaptor.forClass(MatchReport.class);
        verify(analysisReportRepository).markCompleted(eq(55L), eq(USER_ID), reportCaptor.capture(), anyString());
        assertEquals(java.util.List.of(), reportCaptor.getValue().matchedSkills());
        assertEquals(java.util.List.of("Analytical and Problem Solving"), reportCaptor.getValue().missingSkills());
        assertEquals(0, reportCaptor.getValue().matchScore());
    }

    @Test
    void acceptsControlledSemanticEvidenceForAJobCapability() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(46L);
        when(resumeRepository.findCompletedParsedJsonByIdAndUserId(RESUME_ID, USER_ID))
                .thenReturn(Optional.of("{\"skills\":[\"Java\",\"JUnit 5\",\"REST APIs\"]}"));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(JOB_DESCRIPTION_ID, USER_ID))
                .thenReturn(Optional.of("{\"requiredSkills\":[\"Programming\",\"Quality Assurance and Testing\",\"Applications Integration\",\"Cloud computing\"]}"));
        reportGenerator.enqueue("""
                {"matchScore":75,"matchedSkills":["Programming","Quality Assurance and Testing","Applications Integration"],"partialMatches":[],"missingSkills":["Cloud computing"],"strengths":["Java programming"],"risks":["Cloud computing"],"recommendations":["Cloud computing"]}
                """);

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        verify(analysisReportRepository).markCompleted(eq(46L), eq(USER_ID), any(MatchReport.class), anyString());
    }

    @Test
    void acceptsAnAiAnswerThatNamesTheResumeEvidenceInsteadOfTheJobCapability() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(47L);
        when(resumeRepository.findCompletedParsedJsonByIdAndUserId(RESUME_ID, USER_ID))
                .thenReturn(Optional.of("{\"skills\":[\"Java\",\"JUnit 5\"]}"));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(JOB_DESCRIPTION_ID, USER_ID))
                .thenReturn(Optional.of("{\"requiredSkills\":[\"Programming\",\"Quality Assurance & Testing\"]}"));
        reportGenerator.enqueue("""
                {"matchScore":100,"matchedSkills":["Java","JUnit"],"partialMatches":[],"missingSkills":[],"strengths":["Java"],"risks":[],"recommendations":[]}
                """);

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        verify(analysisReportRepository).markCompleted(eq(47L), eq(USER_ID), any(MatchReport.class), anyString());
    }

    @Test
    void calibratesProblemSolvingEvidenceAsAPartialMatch() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(48L);
        when(resumeRepository.findCompletedParsedJsonByIdAndUserId(RESUME_ID, USER_ID))
                .thenReturn(Optional.of("{\"skills\":[\"Java\"],\"projects\":[\"Solved backend workflow problems\"]}"));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(JOB_DESCRIPTION_ID, USER_ID))
                .thenReturn(Optional.of("{\"requiredSkills\":[\"Analytical and problem solving skills\"]}"));
        reportGenerator.enqueue("""
                {"matchScore":70,"matchedSkills":["Analytical and problem solving skills"],"partialMatches":[],"missingSkills":[],"strengths":["Backend problem solving"],"risks":[],"recommendations":[]}
                """);

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        ArgumentCaptor<MatchReport> reportCaptor = ArgumentCaptor.forClass(MatchReport.class);
        verify(analysisReportRepository).markCompleted(eq(48L), eq(USER_ID), reportCaptor.capture(), anyString());
        assertEquals(java.util.List.of(), reportCaptor.getValue().matchedSkills());
        assertEquals(java.util.List.of("Analytical and Problem Solving"), reportCaptor.getValue().partialMatches());
        assertEquals(50, reportCaptor.getValue().matchScore());
    }

    @Test
    void acceptsAConciseMissingSkillThatSharesDistinctiveJobEvidence() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(49L);
        when(resumeRepository.findCompletedParsedJsonByIdAndUserId(RESUME_ID, USER_ID))
                .thenReturn(Optional.of("{\"skills\":[\"Java\"]}"));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(JOB_DESCRIPTION_ID, USER_ID))
                .thenReturn(Optional.of("{\"requiredSkills\":[\"Cloud computing\",\"Business requirements definition, analysis and mapping\"]}"));
        reportGenerator.enqueue("""
                {"matchScore":60,"matchedSkills":[],"partialMatches":[],"missingSkills":["Cloud platform experience","Business requirements analysis"],"strengths":[],"risks":[],"recommendations":[]}
                """);

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        verify(analysisReportRepository).markCompleted(eq(49L), eq(USER_ID), any(MatchReport.class), anyString());
    }

    @Test
    void ignoresAnInventedMissingSkillAndUsesLocalEvidenceForTheCanonicalRequirement() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(50L);
        String inventedMissingSkill = VALID_REPORT_JSON.replace("[\"Docker\"]", "[\"Kubernetes\"]");
        reportGenerator.enqueue(inventedMissingSkill);

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        ArgumentCaptor<MatchReport> reportCaptor = ArgumentCaptor.forClass(MatchReport.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(analysisReportRepository).markCompleted(eq(50L), eq(USER_ID), reportCaptor.capture(), jsonCaptor.capture());
        assertEquals(java.util.List.of("DevOps and Software Delivery"), reportCaptor.getValue().partialMatches());
        assertEquals(java.util.List.of(), reportCaptor.getValue().missingSkills());
        assertEquals(false, jsonCaptor.getValue().contains("Kubernetes"));
        verify(analysisReportRepository, never()).markFailed(eq(50L), anyLong(), anyString(), anyString());
    }

    @Test
    void deduplicatesDetailedResponsibilitiesIntoFixedScoredCapabilities() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(56L);
        when(resumeRepository.findCompletedParsedJsonByIdAndUserId(RESUME_ID, USER_ID))
                .thenReturn(Optional.of("{\"skills\":[\"Java\"]}"));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(JOB_DESCRIPTION_ID, USER_ID))
                .thenReturn(Optional.of("""
                        {
                          "requiredSkills":["Creative thinking","Analytical and problem solving skills"],
                          "responsibilities":[
                            "Think creatively and propose new solutions",
                            "Exercise judgment to identify, diagnose, and solve problems",
                            "Translate user requirements into technical specifications",
                            "Assist in interpreting and documenting client requirements"
                          ]
                        }
                        """));
        reportGenerator.enqueue("""
                {
                  "matchScore":0,
                  "matchedSkills":[],
                  "partialMatches":[],
                  "missingSkills":[
                    "Creative thinking",
                    "Think creatively and propose new solutions",
                    "Exercise judgment to identify, diagnose, and solve problems",
                    "Translate user requirements into technical specifications",
                    "Assist in interpreting and documenting client requirements"
                  ],
                  "strengths":[],
                  "risks":["No direct requirements evidence"],
                  "recommendations":["Add a requirements example"]
                }
                """);

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        ArgumentCaptor<MatchReport> reportCaptor = ArgumentCaptor.forClass(MatchReport.class);
        verify(analysisReportRepository).markCompleted(eq(56L), eq(USER_ID), reportCaptor.capture(), anyString());
        assertEquals(java.util.List.of(
                "Requirements Analysis", "Analytical and Problem Solving"
        ), reportCaptor.getValue().missingSkills());
        assertEquals(2, reportCaptor.getValue().missingSkills().size());
        assertEquals(0, reportCaptor.getValue().matchScore());
    }

    @Test
    void promotesAmpersandSoftSkillsFromMissingToPartialAndCalibratesTheScore() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(53L);
        when(resumeRepository.findCompletedParsedJsonByIdAndUserId(RESUME_ID, USER_ID)).thenReturn(Optional.of("""
                {"experience":["Collaborated with team members","Explaining app features and communicated product value"],"projects":["Designed workflow analytics and authorization"]}
                """));
        when(jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(JOB_DESCRIPTION_ID, USER_ID)).thenReturn(Optional.of("""
                {"requiredSkills":["Verbal & written communication skills","Collaboration & team skills","Analytical and problem solving skills"]}
                """));
        reportGenerator.enqueue("""
                {"matchScore":0,"matchedSkills":[],"partialMatches":[],"missingSkills":["Verbal & written communication skills","Collaboration & team skills","Analytical and problem solving skills"],"strengths":[],"risks":[],"recommendations":[]}
                """);

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        ArgumentCaptor<MatchReport> reportCaptor = ArgumentCaptor.forClass(MatchReport.class);
        verify(analysisReportRepository).markCompleted(eq(53L), eq(USER_ID), reportCaptor.capture(), anyString());
        assertEquals(3, reportCaptor.getValue().partialMatches().size());
        assertEquals(java.util.List.of(), reportCaptor.getValue().missingSkills());
        assertEquals(50, reportCaptor.getValue().matchScore());
    }

    @Test
    void readsHistoricalReportsWithoutPartialMatches() {
        when(analysisReportRepository.findByIdAndUserId(54L, USER_ID)).thenReturn(Optional.of(
                new AnalysisReportRepository.StoredAnalysisReport(
                        54L, RESUME_ID, JOB_DESCRIPTION_ID, AnalysisStatus.COMPLETED, 50,
                        """
                        {"matchScore":50,"matchedSkills":["Java"],"missingSkills":["Docker"],"strengths":[],"risks":[],"recommendations":[]}
                        """,
                        null,
                        null, null, Instant.parse("2026-08-31T20:00:00Z"), null,
                        Instant.parse("2026-08-31T20:00:01Z")
                )
        ));

        AnalysisReportView historical = service.get(USER_ID, 54L);

        assertEquals(java.util.List.of(), historical.report().partialMatches());
    }

    @Test
    void providerTimeoutBecomesSafeFailureWithoutRetrying() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(44L);
        reportGenerator.enqueue(new IllegalStateException("timeout details must not persist"));

        service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID);

        assertEquals(1, reportGenerator.calls);
        verify(analysisReportRepository).markFailed(
                44L,
                USER_ID,
                "REPORT_GENERATION_FAILED",
                "The report could not be generated. Please try again."
        );
    }

    @Test
    void rerunsCreateSeparateHistoricalAnalyses() {
        when(analysisReportRepository.create(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)).thenReturn(51L, 52L);
        reportGenerator.enqueue(VALID_REPORT_JSON);
        reportGenerator.enqueue(VALID_REPORT_JSON);

        assertEquals(51L, service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID));
        assertEquals(52L, service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID));

        verify(analysisReportRepository).markCompleted(eq(51L), eq(USER_ID), any(MatchReport.class), anyString());
        verify(analysisReportRepository).markCompleted(eq(52L), eq(USER_ID), any(MatchReport.class), anyString());
    }

    @Test
    void doesNotCreateAnAnalysisWhenAnInputIsNotCompletedAndOwned() {
        when(resumeRepository.findCompletedParsedJsonByIdAndUserId(RESUME_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(
                InvalidAnalysisInputException.class,
                () -> service.generate(USER_ID, RESUME_ID, JOB_DESCRIPTION_ID)
        );

        verify(analysisReportRepository, never()).create(anyLong(), anyLong(), anyLong());
    }

    private static class FakeReportGenerator implements ReportGenerator {

        private final Queue<Object> responses = new ArrayDeque<>();
        private int calls;

        void enqueue(Object response) {
            responses.add(response);
        }

        @Override
        public String generate(String resumeParsedJson, String jobDescriptionParsedJson) {
            calls++;
            Object response = responses.remove();
            if (response instanceof RuntimeException exception) {
                throw exception;
            }
            return (String) response;
        }
    }
}
