package com.hermanli.careerpilot.interview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermanli.careerpilot.analysis.AnalysisReportService;
import com.hermanli.careerpilot.analysis.AnalysisReportView;
import com.hermanli.careerpilot.analysis.AnalysisStatus;
import com.hermanli.careerpilot.analysis.MatchReport;
import com.hermanli.careerpilot.ai.AiAvailability;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.documents.JobDescriptionRepository;
import com.hermanli.careerpilot.documents.ResumeRepository;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InterviewPreparationService {

    private static final int MAX_ATTEMPTS = 2;
    private static final Logger LOGGER = LoggerFactory.getLogger(InterviewPreparationService.class);
    private static final Set<String> TOOL_TERMS = Set.of("java", "python", "docker", "kubernetes", "aws", "azure",
            "gcp", "react", "spring", "postgresql", "mysql", "mongodb", "terraform", "jenkins", "github actions");
    private static final Pattern METRIC = Pattern.compile("(?:[$£€]\\s?\\d+(?:\\.\\d+)?)|(?:\\d+(?:\\.\\d+)?\\s?%)|(?:\\d+(?:\\.\\d+)?\\s+(?:percent|users|hours|days|weeks|months|dollars))");
    private static final Pattern GAP_PAST_EXPERIENCE_QUESTION = Pattern.compile(
            "\\b(?:how|when|where) did you\\b|\\bwhich project did you\\b|\\btell (?:me|us) about a time\\b"
                    + "|\\bdescribe (?:a|the) (?:time|project|experience|situation)\\s+(?:when|where|in which)\\b"
    );
    private static final Pattern BEHAVIORAL_CONDITION = Pattern.compile(
            "\\b(?:if any|if applicable|if this occurred|only if supported by your actual experience)\\b"
    );

    private final AnalysisReportService analysisReportService;
    private final ResumeRepository resumeRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final InterviewPreparationRepository repository;
    private final InterviewQuestionGenerator generator;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AiAvailability aiAvailability;

    public InterviewPreparationService(
            AnalysisReportService analysisReportService,
            ResumeRepository resumeRepository,
            JobDescriptionRepository jobDescriptionRepository,
            InterviewPreparationRepository repository,
            InterviewQuestionGenerator generator,
            ObjectMapper objectMapper,
            Validator validator,
            AiAvailability aiAvailability
    ) {
        this.analysisReportService = analysisReportService;
        this.resumeRepository = resumeRepository;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.repository = repository;
        this.generator = generator;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.aiAvailability = aiAvailability;
    }

    public CreationResult create(long userId, long analysisId) {
        AnalysisReportView analysis = analysisReportService.get(userId, analysisId);
        if (analysis.status() != AnalysisStatus.COMPLETED || analysis.report() == null) {
            throw new InvalidInterviewPreparationStateException();
        }
        InterviewSessionView existing = repository.findByAnalysisReportIdAndUserId(analysisId, userId).orElse(null);
        if (existing != null) {
            return new CreationResult(existing, false);
        }
        aiAvailability.requireEnabled();
        String resumeJson = resumeRepository.findCompletedParsedJsonByIdAndUserId(analysis.resumeId(), userId)
                .orElseThrow(InvalidInterviewPreparationStateException::new);
        String jobDescriptionJson = jobDescriptionRepository
                .findCompletedParsedJsonByIdAndUserId(analysis.jobDescriptionId(), userId)
                .orElseThrow(InvalidInterviewPreparationStateException::new);
        EvidenceContext evidence = evidence(analysis.report(), resumeJson, jobDescriptionJson);
        InterviewPreparationDraft draft = generate(evidence);
        try {
            return new CreationResult(repository.create(userId, analysisId, "Interview Preparation", draft), true);
        } catch (DataIntegrityViolationException exception) {
            return new CreationResult(repository.findByAnalysisReportIdAndUserId(analysisId, userId)
                    .orElseThrow(() -> exception), false);
        }
    }

    public InterviewSessionView get(long userId, long sessionId) {
        return repository.findByIdAndUserId(sessionId, userId).orElseThrow(ResourceNotFoundException::new);
    }

    private InterviewPreparationDraft generate(EvidenceContext evidence) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                String generated = generator.generate(objectMapper.writeValueAsString(evidence.modelContext()));
                GeneratedInterviewPreparationDraft generatedDraft = objectMapper.readerFor(GeneratedInterviewPreparationDraft.class)
                        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .readValue(removeCodeFence(generated));
                if (!validator.validate(generatedDraft).isEmpty()) {
                    throw reject(InterviewOutputRejectionCategory.INVALID_JSON_STRUCTURE);
                }
                return validate(resolveEvidenceIds(generatedDraft, evidence), evidence);
            } catch (JsonProcessingException exception) {
                logRejected(InterviewOutputRejectionCategory.INVALID_JSON_STRUCTURE, attempt + 1);
            } catch (InvalidInterviewQuestionException exception) {
                logRejected(exception.category(), attempt + 1);
            } catch (RuntimeException exception) {
                LOGGER.warn("Interview preparation generation unavailable: PROVIDER_RUNTIME");
                throw new InterviewModelUnavailableException();
            }
        }
        throw reject(InterviewOutputRejectionCategory.INVALID_JSON_STRUCTURE);
    }

    private InterviewPreparationDraft validate(InterviewPreparationDraft draft, EvidenceContext evidence) {
        Set<String> questionTexts = new HashSet<>();
        int technical = 0;
        int project = 0;
        int behavioral = 0;
        for (InterviewQuestionDraft question : draft.questions()) {
            if (!questionTexts.add(normalize(question.questionText()))) {
                throw reject(InterviewOutputRejectionCategory.DUPLICATE_QUESTION);
            }
            boolean experienceQuestion = question.questionType() == InterviewQuestionType.PROJECT_FOLLOW_UP
                    || question.questionType() == InterviewQuestionType.BEHAVIORAL_EVIDENCE;
            if (experienceQuestion && !evidence.experienceEvidence().containsValue(question.sourceEvidence())) {
                throw reject(InterviewOutputRejectionCategory.UNKNOWN_OR_NON_EXACT_EVIDENCE);
            }
            if (!experienceQuestion && !evidence.gapEvidence().containsValue(question.sourceEvidence())) {
                throw reject(InterviewOutputRejectionCategory.UNKNOWN_OR_NON_EXACT_EVIDENCE);
            }
            if (question.questionType() == InterviewQuestionType.TECHNICAL_GAP
                    && GAP_PAST_EXPERIENCE_QUESTION.matcher(normalize(question.questionText())).find()) {
                throw reject(InterviewOutputRejectionCategory.GAP_AS_CLAIMED_EXPERIENCE);
            }
            if (question.questionType() == InterviewQuestionType.BEHAVIORAL_EVIDENCE
                    && !BEHAVIORAL_CONDITION.matcher(normalize(question.questionText())).find()) {
                throw reject(InterviewOutputRejectionCategory.BEHAVIORAL_SCENARIO_NOT_EVIDENCE_SAFE);
            }
            validateSafeLanguage(question, evidence.allEvidenceText());
            switch (question.questionType()) {
                case TECHNICAL_GAP -> technical++;
                case PROJECT_FOLLOW_UP -> project++;
                case BEHAVIORAL_EVIDENCE -> behavioral++;
            }
        }
        if ((!evidence.gapEvidence().isEmpty() && technical == 0)
                || (!evidence.experienceEvidence().isEmpty() && (project == 0 || behavioral == 0))) {
            throw reject(InterviewOutputRejectionCategory.MISSING_REQUIRED_TYPE);
        }
        return draft;
    }

    private InterviewPreparationDraft resolveEvidenceIds(
            GeneratedInterviewPreparationDraft generatedDraft,
            EvidenceContext evidence
    ) {
        List<InterviewQuestionDraft> questions = generatedDraft.questions().stream().map(question -> {
            String sourceEvidence = switch (question.questionType()) {
                case TECHNICAL_GAP -> evidence.gapEvidenceById().get(question.sourceEvidenceId());
                case PROJECT_FOLLOW_UP, BEHAVIORAL_EVIDENCE -> evidence.experienceEvidenceById()
                        .get(question.sourceEvidenceId());
            };
            if (sourceEvidence == null) {
                throw reject(InterviewOutputRejectionCategory.UNKNOWN_OR_NON_EXACT_EVIDENCE);
            }
            return new InterviewQuestionDraft(question.questionType(), question.questionText(), question.assessmentGoal(),
                    sourceEvidence, question.preparationTip());
        }).toList();
        return new InterviewPreparationDraft(questions);
    }

    private void validateSafeLanguage(InterviewQuestionDraft question, String allEvidenceText) {
        String text = normalize(question.questionText() + " " + question.assessmentGoal() + " " + question.preparationTip());
        if (containsAny(text, "hypothetical", "fictional", "made-up", "made up", "imagined", "imagine", "sample star", "answer")) {
            throw reject(InterviewOutputRejectionCategory.ANSWER_OR_HYPOTHETICAL_CONTENT);
        }
        for (String tool : TOOL_TERMS) {
            if (text.contains(tool) && !allEvidenceText.contains(tool)) {
                throw reject(InterviewOutputRejectionCategory.UNSUPPORTED_TOOL);
            }
        }
        Matcher metrics = METRIC.matcher(text);
        while (metrics.find()) {
            if (!allEvidenceText.contains(metrics.group())) {
                throw reject(InterviewOutputRejectionCategory.UNSUPPORTED_METRIC);
            }
        }
    }

    private EvidenceContext evidence(MatchReport report, String resumeJson, String jobDescriptionJson) {
        Map<String, String> gaps = new LinkedHashMap<>();
        add(gaps, report.partialMatches());
        add(gaps, report.missingSkills());
        add(gaps, report.risks());
        add(gaps, report.recommendations());
        try {
            JsonNode jobDescription = objectMapper.readTree(jobDescriptionJson);
            if (jobDescription == null || !jobDescription.isObject()) {
                throw new InvalidInterviewPreparationStateException();
            }
            collectLeaves(jobDescription, gaps);
            Map<String, String> experience = new LinkedHashMap<>();
            JsonNode resume = objectMapper.readTree(resumeJson);
            if (resume == null || !resume.isObject()) {
                throw new InvalidInterviewPreparationStateException();
            }
            collectExperienceLeaves(resume.path("projects"), experience);
            collectExperienceLeaves(resume.path("workExperience"), experience);
            if (gaps.isEmpty() && experience.isEmpty()) {
                throw new InvalidInterviewPreparationStateException();
            }
            return new EvidenceContext(gaps, experience,
                    normalize(String.join(" ", gaps.values()) + " " + String.join(" ", experience.values())),
                    evidenceIds("G", gaps.values()), evidenceIds("E", experience.values()));
        } catch (JsonProcessingException exception) {
            throw new InvalidInterviewPreparationStateException();
        }
    }

    private void add(Map<String, String> target, List<String> values) {
        values.forEach(value -> target.putIfAbsent(normalize(value), value));
    }

    private void collectLeaves(JsonNode node, Map<String, String> target) {
        if (node.isTextual() && !node.asText().isBlank()) {
            target.putIfAbsent(normalize(node.asText()), node.asText());
            return;
        }
        node.elements().forEachRemaining(child -> collectLeaves(child, target));
    }

    private void collectExperienceLeaves(JsonNode node, Map<String, String> target) {
        if (node.isMissingNode() || node.isNull()) {
            return;
        }
        collectLeaves(node, target);
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private Map<String, String> evidenceIds(String prefix, java.util.Collection<String> values) {
        Map<String, String> evidence = new LinkedHashMap<>();
        int index = 1;
        for (String value : values) {
            evidence.put(prefix + index++, value);
        }
        return java.util.Collections.unmodifiableMap(evidence);
    }

    private static String removeCodeFence(String generated) {
        if (generated == null || generated.isBlank()) throw reject(InterviewOutputRejectionCategory.INVALID_JSON_STRUCTURE);
        String trimmed = generated.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0 || !trimmed.endsWith("```")) {
            throw reject(InterviewOutputRejectionCategory.INVALID_JSON_STRUCTURE);
        }
        return trimmed.substring(firstLineEnd + 1, trimmed.length() - 3).trim();
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static InvalidInterviewQuestionException reject(InterviewOutputRejectionCategory category) {
        return new InvalidInterviewQuestionException(category);
    }

    private void logRejected(InterviewOutputRejectionCategory category, int attempt) {
        LOGGER.warn("Interview preparation output rejected: {} attempt={}", category, attempt);
    }

    private record EvidenceContext(
            Map<String, String> gapEvidence,
            Map<String, String> experienceEvidence,
            String allEvidenceText,
            Map<String, String> gapEvidenceById,
            Map<String, String> experienceEvidenceById
    ) {

        private ModelEvidenceContext modelContext() {
            List<String> requiredQuestionTypes = new ArrayList<>();
            if (!gapEvidence.isEmpty()) {
                requiredQuestionTypes.add(InterviewQuestionType.TECHNICAL_GAP.name());
            }
            if (!experienceEvidence.isEmpty()) {
                requiredQuestionTypes.add(InterviewQuestionType.PROJECT_FOLLOW_UP.name());
                requiredQuestionTypes.add(InterviewQuestionType.BEHAVIORAL_EVIDENCE.name());
            }
            return new ModelEvidenceContext(evidenceReferences(gapEvidenceById), evidenceReferences(experienceEvidenceById),
                    List.copyOf(requiredQuestionTypes));
        }

        private List<ModelEvidenceReference> evidenceReferences(Map<String, String> evidenceById) {
            return evidenceById.entrySet().stream().map(entry -> new ModelEvidenceReference(entry.getKey(), entry.getValue()))
                    .toList();
        }
    }

    private record ModelEvidenceContext(
            List<ModelEvidenceReference> gapEvidence,
            List<ModelEvidenceReference> experienceEvidence,
            List<String> requiredQuestionTypes
    ) {
    }

    private record ModelEvidenceReference(String evidenceId, String text) {
    }

    public record CreationResult(InterviewSessionView session, boolean created) {
    }
}
