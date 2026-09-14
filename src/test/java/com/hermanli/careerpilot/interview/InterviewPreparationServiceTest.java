package com.hermanli.careerpilot.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermanli.careerpilot.ai.AiAvailability;
import com.hermanli.careerpilot.ai.AiUnavailableException;
import com.hermanli.careerpilot.analysis.AnalysisReportService;
import com.hermanli.careerpilot.analysis.AnalysisReportView;
import com.hermanli.careerpilot.analysis.AnalysisStatus;
import com.hermanli.careerpilot.analysis.MatchReport;
import com.hermanli.careerpilot.documents.JobDescriptionRepository;
import com.hermanli.careerpilot.documents.ResumeRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewPreparationServiceTest {

    @Test
    void persistsOnlyValidatedQuestionsAndReturnsCreatedSession() {
        Fixture fixture = fixture(VALID_JSON);
        InterviewSessionView saved = session(88L);
        when(fixture.repository.create(eq(7L), eq(41L), eq("Interview Preparation"), any())).thenReturn(saved);

        InterviewPreparationService.CreationResult result = fixture.service.create(7L, 41L);

        assertEquals(saved, result.session());
        assertEquals(true, result.created());
        ArgumentCaptor<InterviewPreparationDraft> persisted = ArgumentCaptor.forClass(InterviewPreparationDraft.class);
        verify(fixture.repository).create(eq(7L), eq(41L), eq("Interview Preparation"), persisted.capture());
        assertEquals(List.of("Docker", "Deploy services", "Java API project", "Collaborated with a team", "Docker"),
                persisted.getValue().questions().stream().map(InterviewQuestionDraft::sourceEvidence).toList());
    }

    @Test
    void sendsOnlyOriginalEvidenceArraysToTheGenerator() throws Exception {
        Fixture fixture = fixture(VALID_JSON);
        when(fixture.repository.create(eq(7L), eq(41L), eq("Interview Preparation"), any())).thenReturn(session(88L));
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);

        fixture.service.create(7L, 41L);

        verify(fixture.generator).generate(context.capture());
        var payload = new ObjectMapper().readTree(context.getValue());
        assertEquals(List.of("G1", "G2"), evidenceIds(payload.path("gapEvidence")));
        assertEquals(List.of("Docker", "Deploy services"), evidenceTexts(payload.path("gapEvidence")));
        assertEquals(List.of("E1", "E2"), evidenceIds(payload.path("experienceEvidence")));
        assertEquals(List.of("Java API project", "Collaborated with a team"), evidenceTexts(payload.path("experienceEvidence")));
        assertEquals(List.of("TECHNICAL_GAP", "PROJECT_FOLLOW_UP", "BEHAVIORAL_EVIDENCE"),
                textValues(payload.path("requiredQuestionTypes")));
        assertTrue(payload.path("gapEvidence").isArray());
        assertTrue(payload.path("experienceEvidence").isArray());
        assertFalse(context.getValue().contains("\"docker\":"));
        assertFalse(context.getValue().contains("allEvidenceText"));
    }

    @Test
    void promptRequiresCharacterForCharacterEvidenceAndCoverage() {
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("sourceEvidenceId"));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("Return sourceEvidenceId only"));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("only a G evidenceId"));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("only an E evidenceId"));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("requiredQuestionTypes"));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("at least one TECHNICAL_GAP"));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("at least one of each"));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("Never use the word \"answer\""));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("TECHNICAL_GAP questions must ask only"));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("\"how did you\""));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("\"how would you\""));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("never invent or add people, roles"));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("What real example, if any"));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("\"if this"));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("never add one as an example"));
        assertTrue(SpringAiInterviewQuestionGenerator.INSTRUCTIONS.contains("value appears in supplied evidence"));
    }

    @Test
    void returnsExistingSessionWithoutCallingModel() {
        Fixture fixture = fixture(VALID_JSON);
        InterviewSessionView existing = session(77L);
        when(fixture.repository.findByAnalysisReportIdAndUserId(41L, 7L)).thenReturn(Optional.of(existing));

        InterviewPreparationService.CreationResult result = fixture.service.create(7L, 41L);

        assertEquals(existing, result.session());
        assertEquals(false, result.created());
        verify(fixture.generator, never()).generate(any());
    }

    @Test
    void rejectsUnknownEvidenceTwiceWithoutPersistence() {
        Fixture fixture = fixture(VALID_JSON.replace("\"sourceEvidenceId\":\"G1\"",
                "\"sourceEvidenceId\":\"G9\""));
        assertRejectedTwice(fixture, InterviewOutputRejectionCategory.UNKNOWN_OR_NON_EXACT_EVIDENCE);
    }

    @Test
    void rejectsDuplicateAndUnknownJsonFieldsTwiceWithoutPersistence() {
        Fixture duplicate = fixture(VALID_JSON.replace("\"questions\":[", "\"questions\":[],\"questions\":["));
        assertRejectedTwice(duplicate, InterviewOutputRejectionCategory.INVALID_JSON_STRUCTURE);

        Fixture unknown = fixture(VALID_JSON.replace("{\"questions\":[", "{\"unexpected\":true,\"questions\":["));
        assertRejectedTwice(unknown, InterviewOutputRejectionCategory.INVALID_JSON_STRUCTURE);

        Fixture whitespace = fixture(VALID_JSON.replace("\"sourceEvidenceId\":\"G1\"",
                "\"sourceEvidenceId\":\" G1\""));
        assertRejectedTwice(whitespace, InterviewOutputRejectionCategory.UNKNOWN_OR_NON_EXACT_EVIDENCE);

        Fixture crossPool = fixture(VALID_JSON.replace("\"sourceEvidenceId\":\"G1\"",
                "\"sourceEvidenceId\":\"E1\""));
        assertRejectedTwice(crossPool, InterviewOutputRejectionCategory.UNKNOWN_OR_NON_EXACT_EVIDENCE);
    }

    @Test
    void classifiesMissingRequiredTypeAndDuplicateQuestion() {
        Fixture missingType = fixture(VALID_JSON
                .replace("Tell us about the Java API project.", "Tell us about the Java API project, if applicable.")
                .replace("\"PROJECT_FOLLOW_UP\"", "\"BEHAVIORAL_EVIDENCE\""));
        assertRejectedTwice(missingType, InterviewOutputRejectionCategory.MISSING_REQUIRED_TYPE);

        Fixture duplicateQuestion = fixture(VALID_JSON.replace("How would you prepare for deploy services?",
                "What would you verify for Docker?"));
        assertRejectedTwice(duplicateQuestion, InterviewOutputRejectionCategory.DUPLICATE_QUESTION);
    }

    @Test
    void classifiesUnsafeAnswerToolAndMetricContent() {
        Fixture answer = fixture(VALID_JSON.replace("Keep the preparation factual.", "Prepare an answer."));
        assertRejectedTwice(answer, InterviewOutputRejectionCategory.ANSWER_OR_HYPOTHETICAL_CONTENT);

        Fixture unsupportedTool = fixture(VALID_JSON.replace("Keep the preparation factual.",
                "Discuss Kubernetes safely."));
        assertRejectedTwice(unsupportedTool, InterviewOutputRejectionCategory.UNSUPPORTED_TOOL);

        Fixture unsupportedMetric = fixture(VALID_JSON.replace("Keep the preparation factual.",
                "Discuss a 25% improvement."));
        assertRejectedTwice(unsupportedMetric, InterviewOutputRejectionCategory.UNSUPPORTED_METRIC);
    }

    @Test
    void rejectsTechnicalGapThatPresupposesClaimedExperience() {
        Fixture fixture = fixture(VALID_JSON.replace("How would you prepare for deploy services?",
                "How did you split Docker work in any project?"));

        assertRejectedTwice(fixture, InterviewOutputRejectionCategory.GAP_AS_CLAIMED_EXPERIENCE);
    }

    @Test
    void rejectsBehavioralQuestionsThatInventOrPresupposeAScenario() {
        Fixture inventedRole = fixture(VALID_JSON.replace("What real collaboration example, if any, can you truthfully share from the documented project?",
                "Describe a time when you advised a hiring manager as a career advisor."));
        assertRejectedTwice(inventedRole, InterviewOutputRejectionCategory.BEHAVIORAL_SCENARIO_NOT_EVIDENCE_SAFE);

        Fixture inventedResolution = fixture(VALID_JSON.replace("What real collaboration example, if any, can you truthfully share from the documented project?",
                "Tell us about a situation where you worked with others to resolve a user expectation gap."));
        assertRejectedTwice(inventedResolution, InterviewOutputRejectionCategory.BEHAVIORAL_SCENARIO_NOT_EVIDENCE_SAFE);
    }

    @Test
    void allowsSafeTechnicalFuturePhrasingAndExperienceWordingForTheOtherQuestionTypes() {
        String response = VALID_JSON
                .replace("Tell us about the Java API project.", "How did you approach the Java API project?")
                .replace("What real collaboration example, if any, can you truthfully share from the documented project?",
                        "Tell us about a time, if any, you collaborated with a team.");
        Fixture fixture = fixture(response);
        when(fixture.repository.create(eq(7L), eq(41L), eq("Interview Preparation"), any())).thenReturn(session(88L));

        InterviewPreparationService.CreationResult result = fixture.service.create(7L, 41L);

        assertEquals(88L, result.session().id());
        verify(fixture.generator).generate(any());
    }

    @Test
    void mapsProviderRuntimeToSafeException() {
        Fixture fixture = fixture(VALID_JSON);
        when(fixture.generator.generate(any())).thenThrow(new IllegalStateException("provider detail"));

        ch.qos.logback.classic.Logger logger = interviewLogger();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(InterviewModelUnavailableException.class, () -> fixture.service.create(7L, 41L));
        } finally {
            logger.detachAppender(appender);
        }
        assertEquals(List.of("Interview preparation generation unavailable: PROVIDER_RUNTIME"), appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
        verify(fixture.repository, never()).create(any(Long.class), any(Long.class), any(String.class), any());
    }

    @Test
    void rejectsOfflineGenerationBeforeCallingModelOrPersistingSession() {
        Fixture fixture = fixture(VALID_JSON, false);

        assertThrows(AiUnavailableException.class, () -> fixture.service.create(7L, 41L));

        verify(fixture.generator, never()).generate(any());
        verify(fixture.repository, never()).create(any(Long.class), any(Long.class), any(String.class), any());
    }

    @Test
    void rejectsCompletedAnalysisWhenItsParsedInputsAreUnavailable() {
        Fixture fixture = fixture(VALID_JSON);
        when(fixture.resumes.findCompletedParsedJsonByIdAndUserId(11L, 7L)).thenReturn(Optional.empty());

        assertThrows(InvalidInterviewPreparationStateException.class, () -> fixture.service.create(7L, 41L));
        verify(fixture.generator, never()).generate(any());
        verify(fixture.repository, never()).create(any(Long.class), any(Long.class), any(String.class), any());
    }

    private Fixture fixture(String response) {
        return fixture(response, true);
    }

    private Fixture fixture(String response, boolean aiEnabled) {
        AnalysisReportService analysis = mock(AnalysisReportService.class);
        ResumeRepository resumes = mock(ResumeRepository.class);
        JobDescriptionRepository jobs = mock(JobDescriptionRepository.class);
        InterviewPreparationRepository repository = mock(InterviewPreparationRepository.class);
        InterviewQuestionGenerator generator = mock(InterviewQuestionGenerator.class);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        when(analysis.get(7L, 41L)).thenReturn(analysis());
        when(repository.findByAnalysisReportIdAndUserId(41L, 7L)).thenReturn(Optional.empty());
        when(resumes.findCompletedParsedJsonByIdAndUserId(11L, 7L)).thenReturn(Optional.of(
                "{\"projects\":[\"Java API project\"],\"workExperience\":[\"Collaborated with a team\"]}"
        ));
        when(jobs.findCompletedParsedJsonByIdAndUserId(12L, 7L)).thenReturn(Optional.of(
                "{\"requiredSkills\":[\"Docker\"],\"responsibilities\":[\"Deploy services\"]}"
        ));
        when(generator.generate(any())).thenReturn(response);
        return new Fixture(new InterviewPreparationService(analysis, resumes, jobs, repository, generator,
                new ObjectMapper(), validator, new AiAvailability(aiEnabled)), repository, generator, resumes);
    }

    private void assertRejectedTwice(Fixture fixture, InterviewOutputRejectionCategory category) {
        ch.qos.logback.classic.Logger logger = interviewLogger();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(InvalidInterviewQuestionException.class, () -> fixture.service.create(7L, 41L));
        } finally {
            logger.detachAppender(appender);
        }
        assertEquals(List.of(
                "Interview preparation output rejected: " + category + " attempt=1",
                "Interview preparation output rejected: " + category + " attempt=2"
        ), appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
        verify(fixture.generator, org.mockito.Mockito.times(2)).generate(any());
        verify(fixture.repository, never()).create(any(Long.class), any(Long.class), any(String.class), any());
    }

    private ch.qos.logback.classic.Logger interviewLogger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(InterviewPreparationService.class);
    }

    private List<String> textValues(com.fasterxml.jackson.databind.JsonNode node) {
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(com.fasterxml.jackson.databind.JsonNode::asText).toList();
    }

    private List<String> evidenceIds(com.fasterxml.jackson.databind.JsonNode node) {
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(item -> item.path("evidenceId").asText()).toList();
    }

    private List<String> evidenceTexts(com.fasterxml.jackson.databind.JsonNode node) {
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(item -> item.path("text").asText()).toList();
    }

    private AnalysisReportView analysis() {
        return new AnalysisReportView(41L, 11L, 12L, AnalysisStatus.COMPLETED,
                new MatchReport(50, List.of(), List.of(), List.of("Docker"), List.of(), List.of(), List.of()),
                5L, null, null, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH);
    }

    private InterviewSessionView session(long id) {
        return new InterviewSessionView(id, 41L, "Interview Preparation", Instant.EPOCH, List.of());
    }

    private record Fixture(InterviewPreparationService service, InterviewPreparationRepository repository,
                           InterviewQuestionGenerator generator, ResumeRepository resumes) {
    }

    private static final String VALID_JSON = """
            {"questions":[
              {"questionType":"TECHNICAL_GAP","questionText":"What would you verify for Docker?","assessmentGoal":"Assess the documented gap.","sourceEvidenceId":"G1","preparationTip":"Review the documented requirement."},
              {"questionType":"TECHNICAL_GAP","questionText":"How would you prepare for deploy services?","assessmentGoal":"Assess requirement understanding.","sourceEvidenceId":"G2","preparationTip":"Map the requirement to real evidence."},
              {"questionType":"PROJECT_FOLLOW_UP","questionText":"Tell us about the Java API project.","assessmentGoal":"Assess the real project evidence.","sourceEvidenceId":"E1","preparationTip":"Use only the documented project details."},
              {"questionType":"BEHAVIORAL_EVIDENCE","questionText":"What real collaboration example, if any, can you truthfully share from the documented project?","assessmentGoal":"Assess the real collaboration evidence.","sourceEvidenceId":"E2","preparationTip":"Describe only the documented collaboration."},
              {"questionType":"TECHNICAL_GAP","questionText":"Which Docker evidence needs verification?","assessmentGoal":"Assess evidence awareness.","sourceEvidenceId":"G1","preparationTip":"Keep the preparation factual."}
            ]}
            """;
}
