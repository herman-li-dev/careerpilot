package com.hermanli.careerpilot.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hermanli.careerpilot.ai.AiAvailability;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.documents.JobDescriptionRepository;
import com.hermanli.careerpilot.documents.ResumeRepository;
import com.hermanli.careerpilot.plan.PlanDraft;
import com.hermanli.careerpilot.plan.PlanGenerationService;
import com.hermanli.careerpilot.plan.PlanPersistenceService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Service
public class AnalysisReportService {

    private static final int MAX_INVALID_OUTPUT_ATTEMPTS = 2;
    private static final String GENERATION_FAILED_CODE = "REPORT_GENERATION_FAILED";
    private static final String GENERATION_FAILED_MESSAGE =
            "The report could not be generated. Please try again.";
    private static final String PLAN_GENERATION_FAILED_CODE = "PLAN_GENERATION_FAILED";
    private static final String PLAN_GENERATION_FAILED_MESSAGE =
            "The preparation plan could not be generated. Please try again.";
    private final ResumeRepository resumeRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final ReportGenerator reportGenerator;
    private final PlanGenerationService planGenerationService;
    private final PlanPersistenceService planPersistenceService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AiAvailability aiAvailability;

    public AnalysisReportService(
            ResumeRepository resumeRepository,
            JobDescriptionRepository jobDescriptionRepository,
            AnalysisReportRepository analysisReportRepository,
            ReportGenerator reportGenerator,
            PlanGenerationService planGenerationService,
            PlanPersistenceService planPersistenceService,
            ObjectMapper objectMapper,
            Validator validator,
            AiAvailability aiAvailability
    ) {
        this.resumeRepository = resumeRepository;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.analysisReportRepository = analysisReportRepository;
        this.reportGenerator = reportGenerator;
        this.planGenerationService = planGenerationService;
        this.planPersistenceService = planPersistenceService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.aiAvailability = aiAvailability;
    }

    public long generate(long userId, long resumeId, long jobDescriptionId) {
        long analysisId = createPending(userId, resumeId, jobDescriptionId);
        if (!analysisReportRepository.markRunning(analysisId, userId)) {
            throw new IllegalStateException("A newly created analysis could not be started.");
        }
        generateRunning(userId, analysisId, resumeId, jobDescriptionId);
        return analysisId;
    }

    public long createPending(long userId, long resumeId, long jobDescriptionId) {
        requireCompletedInputs(userId, resumeId, jobDescriptionId);
        aiAvailability.requireEnabled();
        return analysisReportRepository.create(userId, resumeId, jobDescriptionId);
    }

    public void runPending(long userId, long analysisId) {
        AnalysisReportRepository.StoredAnalysisReport analysis = analysisReportRepository
                .findByIdAndUserId(analysisId, userId)
                .orElseThrow(ResourceNotFoundException::new);
        if (analysis.status() != AnalysisStatus.PENDING || !analysisReportRepository.markRunning(analysisId, userId)) {
            return;
        }
        generateRunning(userId, analysisId, analysis.resumeId(), analysis.jobDescriptionId());
    }

    public List<AnalysisReportView> list(long userId) {
        return analysisReportRepository.findAllByUserId(userId).stream().map(this::toView).toList();
    }

    public AnalysisReportView get(long userId, long analysisId) {
        return analysisReportRepository.findByIdAndUserId(analysisId, userId)
                .map(this::toView)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private void requireCompletedInputs(long userId, long resumeId, long jobDescriptionId) {
        resumeRepository.findCompletedParsedJsonByIdAndUserId(resumeId, userId)
                .orElseThrow(InvalidAnalysisInputException::new);
        jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(jobDescriptionId, userId)
                .orElseThrow(InvalidAnalysisInputException::new);
    }

    private void generateRunning(long userId, long analysisId, long resumeId, long jobDescriptionId) {
        String resumeJson = resumeRepository.findCompletedParsedJsonByIdAndUserId(resumeId, userId)
                .orElseThrow(InvalidAnalysisInputException::new);
        String jobDescriptionJson = jobDescriptionRepository.findCompletedParsedJsonByIdAndUserId(jobDescriptionId, userId)
                .orElseThrow(InvalidAnalysisInputException::new);

        for (int attempt = 0; attempt < MAX_INVALID_OUTPUT_ATTEMPTS; attempt++) {
            try {
                String generatedJson = reportGenerator.generate(resumeJson, jobDescriptionJson);
                MatchReport report = validateReport(generatedJson, resumeJson, jobDescriptionJson);
                PlanDraft plan = generatePlan(report, jobDescriptionJson);
                planPersistenceService.savePlanAndCompleteAnalysis(
                        userId, analysisId, report, objectMapper.writeValueAsString(report), plan
                );
                return;
            } catch (PlanGenerationService.InvalidPlanException exception) {
                analysisReportRepository.markFailed(
                        analysisId, userId, PLAN_GENERATION_FAILED_CODE, PLAN_GENERATION_FAILED_MESSAGE
                );
                return;
            } catch (InvalidGeneratedReportException exception) {
                if (attempt + 1 == MAX_INVALID_OUTPUT_ATTEMPTS) {
                    analysisReportRepository.markFailed(
                            analysisId, userId, exception.failure.code, exception.failure.message
                    );
                    return;
                }
            } catch (JsonProcessingException exception) {
                if (attempt + 1 == MAX_INVALID_OUTPUT_ATTEMPTS) {
                    analysisReportRepository.markFailed(
                            analysisId, userId, ValidationFailure.SCHEMA.code, ValidationFailure.SCHEMA.message
                    );
                    return;
                }
            } catch (RuntimeException exception) {
                analysisReportRepository.markFailed(
                        analysisId, userId, GENERATION_FAILED_CODE, GENERATION_FAILED_MESSAGE
                );
                return;
            }
        }
        throw new IllegalStateException("Report generation attempts were unexpectedly exhausted.");
    }

    private AnalysisReportView toView(AnalysisReportRepository.StoredAnalysisReport report) {
        MatchReport matchReport = null;
        if (report.reportJson() != null) {
            try {
                JsonNode persistedJson = objectMapper.readTree(report.reportJson());
                if (persistedJson instanceof ObjectNode objectNode && !objectNode.has("partialMatches")) {
                    objectNode.putArray("partialMatches");
                }
                matchReport = objectMapper.treeToValue(persistedJson, MatchReport.class);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("A persisted report could not be read.");
            }
        }
        return new AnalysisReportView(
                report.id(), report.resumeId(), report.jobDescriptionId(), report.status(), matchReport,
                report.planId(), report.errorCode(), report.errorMessage(), report.createdAt(), report.startedAt(), report.completedAt()
        );
    }

    private PlanDraft generatePlan(MatchReport report, String jobDescriptionJson) {
        PlanGenerationService.InvalidPlanException lastFailure = null;
        for (int attempt = 0; attempt < MAX_INVALID_OUTPUT_ATTEMPTS; attempt++) {
            try {
                return planGenerationService.generate(report, jobDescriptionJson);
            } catch (PlanGenerationService.InvalidPlanException exception) {
                lastFailure = exception;
            }
        }
        PlanDraft fallback = planGenerationService.generateSafeFallback(report, jobDescriptionJson);
        if (fallback == null) {
            throw lastFailure;
        }
        return fallback;
    }

    private MatchReport validateReport(String generatedJson, String resumeJson, String jobDescriptionJson) {
        try {
            if (generatedJson == null || generatedJson.isBlank()) {
                throw new InvalidGeneratedReportException(ValidationFailure.SCHEMA);
            }
            MatchReport report = objectMapper.readerFor(MatchReport.class)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .readValue(removeCodeFence(generatedJson));
            Set<ConstraintViolation<MatchReport>> violations = validator.validate(report);
            if (!violations.isEmpty()) {
                throw new InvalidGeneratedReportException(ValidationFailure.CONSTRAINTS);
            }
            return sanitizeEvidence(report, resumeJson, jobDescriptionJson);
        } catch (JsonProcessingException exception) {
            throw new InvalidGeneratedReportException(ValidationFailure.SCHEMA);
        }
    }

    private MatchReport sanitizeEvidence(MatchReport report, String resumeJson, String jobDescriptionJson) {
        Set<String> resumeEvidence = textValues(resumeJson);
        Set<String> jobDescriptionEvidence = textValues(jobDescriptionJson);
        Set<ScoredCapability> matchedClaims = claims(report.matchedSkills());
        List<String> matchedSkills = new ArrayList<>();
        List<String> partialMatches = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();

        for (ScoredCapability capability : ScoredCapability.values()) {
            if (!capability.requiredBy(jobDescriptionEvidence)) {
                continue;
            }
            boolean resumeSupport = capability.supportedBy(resumeEvidence);
            if (matchedClaims.contains(capability) && resumeSupport && !capability.relatedEvidenceIsPartial()) {
                matchedSkills.add(capability.label());
            } else if (resumeSupport) {
                partialMatches.add(capability.label());
            } else {
                missingSkills.add(capability.label());
            }
        }
        int calibratedScore = calibratedScore(matchedSkills, partialMatches, missingSkills);
        return new MatchReport(
                calibratedScore,
                List.copyOf(matchedSkills),
                List.copyOf(partialMatches),
                List.copyOf(missingSkills),
                report.strengths(),
                report.risks(),
                report.recommendations()
        );
    }

    private int calibratedScore(List<String> matchedSkills, List<String> partialMatches, List<String> missingSkills) {
        int total = matchedSkills.size() + partialMatches.size() + missingSkills.size();
        if (total == 0) {
            return 0;
        }
        return Math.round((matchedSkills.size() * 100f + partialMatches.size() * 50f) / total);
    }

    private Set<ScoredCapability> claims(List<String> items) {
        Set<ScoredCapability> claims = EnumSet.noneOf(ScoredCapability.class);
        items.forEach(item -> ScoredCapability.fromReportItem(item).ifPresent(claims::add));
        return claims;
    }

    private String removeCodeFence(String generatedJson) {
        String trimmed = generatedJson.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0 || !trimmed.endsWith("```")) {
            throw new InvalidGeneratedReportException(ValidationFailure.SCHEMA);
        }
        return trimmed.substring(firstLineEnd + 1, trimmed.length() - 3).trim();
    }

    private Set<String> textValues(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new InvalidGeneratedReportException(ValidationFailure.INPUT_EVIDENCE);
            }
            Set<String> values = new HashSet<>();
            collectTextValues(root, values);
            return values;
        } catch (JsonProcessingException exception) {
            throw new InvalidGeneratedReportException(ValidationFailure.INPUT_EVIDENCE);
        }
    }

    private void collectTextValues(JsonNode node, Set<String> values) {
        if (node.isTextual()) {
            values.add(ScoredCapability.normalize(node.asText()));
            return;
        }
        Iterator<JsonNode> children = node.elements();
        while (children.hasNext()) {
            collectTextValues(children.next(), values);
        }
    }

    private enum ValidationFailure {
        SCHEMA("INVALID_REPORT_SCHEMA", "The model returned an invalid report structure. Please try again."),
        CONSTRAINTS("INVALID_REPORT_CONSTRAINTS", "The model returned an invalid report score or field value. Please try again."),
        INPUT_EVIDENCE("INVALID_INPUT_EVIDENCE", "The saved parsed inputs could not be checked safely.");

        private final String code;
        private final String message;

        ValidationFailure(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    private static class InvalidGeneratedReportException extends RuntimeException {

        private final ValidationFailure failure;

        private InvalidGeneratedReportException(ValidationFailure failure) {
            this.failure = failure;
        }
    }
}
