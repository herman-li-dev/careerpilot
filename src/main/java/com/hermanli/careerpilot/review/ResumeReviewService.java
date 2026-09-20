package com.hermanli.careerpilot.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.documents.InvalidDocumentStateException;
import com.hermanli.careerpilot.documents.Resume;
import com.hermanli.careerpilot.documents.ResumeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ResumeReviewService {

    private static final Logger log = LoggerFactory.getLogger(ResumeReviewService.class);
    private static final int MAX_RETRIEVED_CANDIDATES = 12;
    private static final int MAX_SUGGESTIONS = 6;
    private static final int MAX_SUGGESTIONS_PER_CATEGORY = 2;
    private static final int MAX_MODEL_ID_LENGTH = 60;
    private static final ObjectMapper STRICT_MODEL_OUTPUT_MAPPER = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()
    ).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    private static final List<EvidenceField> EVIDENCE_FIELDS = List.of(
            new EvidenceField("skills", "SKILLS"),
            new EvidenceField("education", "EDUCATION"),
            new EvidenceField("projects", "PROJECTS"),
            new EvidenceField("workExperience", "EXPERIENCE"),
            new EvidenceField("certifications", "CREDENTIALS")
    );
    private static final Comparator<ReviewSuggestion> SUGGESTION_ORDER = Comparator
            .comparingInt((ReviewSuggestion suggestion) -> priorityOrder(suggestion.priority()))
            .thenComparing(ReviewSuggestion::category)
            .thenComparing(ReviewSuggestion::sourceId)
            .thenComparing(ReviewSuggestion::resumeEvidence);

    private final ResumeRepository resumeRepository;
    private final ReviewKnowledgeBase reviewKnowledgeBase;
    private final ObjectMapper objectMapper;
    private final ResumeReviewGenerator resumeReviewGenerator;
    private final SyntheticVectorRag syntheticVectorRag;

    public ResumeReviewService(
            ResumeRepository resumeRepository,
            ReviewKnowledgeBase reviewKnowledgeBase,
            ObjectMapper objectMapper,
            ResumeReviewGenerator resumeReviewGenerator
    ) {
        this(resumeRepository, reviewKnowledgeBase, objectMapper, resumeReviewGenerator, (SyntheticVectorRag) null);
    }

    @Autowired
    public ResumeReviewService(
            ResumeRepository resumeRepository,
            ReviewKnowledgeBase reviewKnowledgeBase,
            ObjectMapper objectMapper,
            ResumeReviewGenerator resumeReviewGenerator,
            org.springframework.beans.factory.ObjectProvider<SyntheticVectorRag> syntheticVectorRag
    ) {
        this.resumeRepository = resumeRepository;
        this.reviewKnowledgeBase = reviewKnowledgeBase;
        this.objectMapper = objectMapper;
        this.resumeReviewGenerator = resumeReviewGenerator;
        this.syntheticVectorRag = syntheticVectorRag.getIfAvailable();
    }

    ResumeReviewService(
            ResumeRepository resumeRepository,
            ReviewKnowledgeBase reviewKnowledgeBase,
            ObjectMapper objectMapper,
            ResumeReviewGenerator resumeReviewGenerator,
            SyntheticVectorRag syntheticVectorRag
    ) {
        this.resumeRepository = resumeRepository;
        this.reviewKnowledgeBase = reviewKnowledgeBase;
        this.objectMapper = objectMapper;
        this.resumeReviewGenerator = resumeReviewGenerator;
        this.syntheticVectorRag = syntheticVectorRag;
    }

    public ResumeReview review(long userId, long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(ResourceNotFoundException::new);
        if (!"COMPLETED".equals(resume.parseStatus())) {
            throw new InvalidDocumentStateException();
        }
        String parsedJson = resumeRepository.findCompletedParsedJsonByIdAndUserId(resumeId, userId)
                .orElseThrow(InvalidDocumentStateException::new);
        List<Evidence> evidence = extractEvidence(parsedJson);
        ResumeReview vectorReview = tryVectorReview(resumeId, evidence);
        if (vectorReview != null) {
            return vectorReview;
        }
        List<ReviewCandidate> retrievedCandidates = selectCandidates(evidence);
        if (retrievedCandidates.isEmpty()) {
            return new ResumeReview(
                    resumeId,
                    "SYNTHETIC_LEXICAL_RULES",
                    reviewKnowledgeBase.version(),
                    List.of()
            );
        }
        List<ReviewCandidate> candidates = diversifyCandidates(retrievedCandidates);
        List<ReviewSuggestion> modelSuggestions = selectWithModel(candidates);
        return new ResumeReview(
                resumeId,
                modelSuggestions == null
                        ? "SYNTHETIC_LEXICAL_RULES_FALLBACK"
                        : "MODEL_ASSISTED_SYNTHETIC_LEXICAL_RULES",
                reviewKnowledgeBase.version(),
                modelSuggestions == null ? suggestionsFrom(candidates) : modelSuggestions
        );
    }

    private ResumeReview tryVectorReview(long resumeId, List<Evidence> evidence) {
        if (syntheticVectorRag == null || evidence.isEmpty()) {
            return null;
        }
        try {
            List<SyntheticVectorRag.RetrievedGuidance> guidance = syntheticVectorRag.retrieve(retrievalQuery(evidence));
            List<ReviewCandidate> candidates = diversifyCandidates(selectVectorCandidates(evidence, guidance));
            if (candidates.isEmpty()) {
                return null;
            }
            List<ReviewSuggestion> modelSuggestions = selectWithModel(candidates);
            if (modelSuggestions == null) {
                return null;
            }
            return new ResumeReview(
                    resumeId,
                    "MODEL_ASSISTED_SYNTHETIC_VECTOR_RAG",
                    SyntheticReviewGuide.SOURCE_VERSION,
                    modelSuggestions
            );
        } catch (RuntimeException exception) {
            // Retrieval failures must not expose resume evidence or provider diagnostics.
            log.warn("Synthetic vector review is unavailable; using lexical fallback.");
            return null;
        }
    }

    private String retrievalQuery(List<Evidence> evidence) {
        int maxContextChars = syntheticVectorRag.maxContextChars();
        StringBuilder query = new StringBuilder("Resume evidence (untrusted data):\n");
        for (Evidence item : evidence) {
            if (query.length() >= maxContextChars) {
                break;
            }
            query.append(item.category()).append(": ").append(item.text()).append('\n');
        }
        return query.length() <= maxContextChars
                ? query.toString()
                : query.substring(0, maxContextChars);
    }

    private List<ReviewCandidate> selectVectorCandidates(
            List<Evidence> evidence,
            List<SyntheticVectorRag.RetrievedGuidance> guidance
    ) {
        List<ReviewCandidate> candidates = new ArrayList<>();
        Set<String> pairs = new HashSet<>();
        for (SyntheticVectorRag.RetrievedGuidance item : guidance) {
            ReviewKnowledgeBase.ReviewRule rule = reviewKnowledgeBase.ruleForCategory(item.chunk().category());
            for (Evidence resumeEvidence : evidence) {
                if (!item.chunk().category().equals(resumeEvidence.category())) {
                    continue;
                }
                String pair = pairKey(item.chunk().id(), resumeEvidence.id());
                if (!pairs.add(pair)) {
                    continue;
                }
                ReviewSuggestion suggestion = new ReviewSuggestion(
                        rule.category(),
                        rule.priority(),
                        "This existing " + rule.category().toLowerCase(Locale.ROOT)
                                + " evidence may benefit from clearer, truthful context.",
                        resumeEvidence.text(),
                        item.chunk().content(),
                        SyntheticReviewGuide.SOURCE_ID,
                        SyntheticReviewGuide.SOURCE_TITLE,
                        item.citation()
                );
                candidates.add(new ReviewCandidate(
                        item.chunk().id(),
                        resumeEvidence.id(),
                        suggestion,
                        new ResumeReviewModelCandidate(
                                item.chunk().id(), resumeEvidence.id(), rule.category(), rule.priority(),
                                item.chunk().content(), resumeEvidence.text()
                        )
                ));
            }
        }
        candidates.sort(Comparator.comparing(ReviewCandidate::suggestion, SUGGESTION_ORDER));
        return candidates.size() <= MAX_RETRIEVED_CANDIDATES
                ? List.copyOf(candidates)
                : List.copyOf(candidates.subList(0, MAX_RETRIEVED_CANDIDATES));
    }

    private List<ReviewCandidate> selectCandidates(List<Evidence> evidence) {
        List<ReviewCandidate> candidates = new ArrayList<>();
        Set<String> uniqueSuggestions = new HashSet<>();
        for (ReviewKnowledgeBase.ReviewRule rule : reviewKnowledgeBase.rules()) {
            for (Evidence item : evidence) {
                if (!rule.category().equals(item.category()) || !matches(rule, item.text())) {
                    continue;
                }
                String key = rule.category() + '\u0000' + rule.sourceId() + '\u0000' + item.text();
                if (!uniqueSuggestions.add(key)) {
                    continue;
                }
                ReviewSuggestion suggestion = new ReviewSuggestion(
                        rule.category(),
                        rule.priority(),
                        "This existing " + rule.category().toLowerCase(Locale.ROOT)
                                + " evidence may benefit from clearer, truthful context.",
                        item.text(),
                        rule.recommendation(),
                        rule.sourceId(),
                        rule.sourceTitle()
                );
                candidates.add(new ReviewCandidate(
                        rule.id(),
                        item.id(),
                        suggestion,
                        new ResumeReviewModelCandidate(
                                rule.id(), item.id(), rule.category(), rule.priority(),
                                rule.recommendation(), item.text()
                        )
                ));
            }
        }
        candidates.sort(Comparator.comparing(ReviewCandidate::suggestion, SUGGESTION_ORDER));
        return candidates.size() <= MAX_RETRIEVED_CANDIDATES
                ? List.copyOf(candidates)
                : List.copyOf(candidates.subList(0, MAX_RETRIEVED_CANDIDATES));
    }

    private List<ReviewSuggestion> selectWithModel(List<ReviewCandidate> candidates) {
        ResumeReviewModelRequest request = new ResumeReviewModelRequest(
                candidates.stream().map(ReviewCandidate::modelCandidate).toList()
        );
        for (int attempt = 1; attempt <= 2; attempt++) {
            String modelOutput;
            try {
                modelOutput = resumeReviewGenerator.generate(request);
            } catch (RuntimeException exception) {
                log.warn("Resume review model provider unavailable; using deterministic fallback.");
                return null;
            }
            try {
                return resolveModelSelections(modelOutput, candidates);
            } catch (InvalidModelOutput exception) {
                log.warn("Resume review model output rejected: category={}, attempt={}", exception.category(), attempt);
            }
        }
        log.warn("Resume review model output rejected twice; using deterministic fallback.");
        return null;
    }

    private List<ReviewSuggestion> resolveModelSelections(String modelOutput, List<ReviewCandidate> candidates) {
        ModelSelectionResponse response;
        try {
            response = STRICT_MODEL_OUTPUT_MAPPER.readValue(modelOutput, ModelSelectionResponse.class);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new InvalidModelOutput(RejectionCategory.MALFORMED_OUTPUT);
        }
        if (response == null || response.suggestions() == null
                || response.suggestions().isEmpty() || response.suggestions().size() > MAX_SUGGESTIONS) {
            throw new InvalidModelOutput(RejectionCategory.INVALID_SELECTION_COUNT);
        }
        java.util.Map<String, ReviewCandidate> byPair = candidates.stream().collect(java.util.stream.Collectors.toMap(
                ReviewCandidate::pairKey,
                candidate -> candidate
        ));
        List<ReviewSuggestion> selected = new ArrayList<>();
        Set<String> selectedPairs = new HashSet<>();
        java.util.Map<String, Integer> categoryCounts = new java.util.HashMap<>();
        for (ModelSelection selection : response.suggestions()) {
            if (selection == null || !validModelId(selection.ruleId()) || !validModelId(selection.evidenceId())) {
                throw new InvalidModelOutput(RejectionCategory.INVALID_IDENTIFIER);
            }
            String pairKey = pairKey(selection.ruleId(), selection.evidenceId());
            if (!selectedPairs.add(pairKey)) {
                throw new InvalidModelOutput(RejectionCategory.DUPLICATE_PAIR);
            }
            ReviewCandidate candidate = byPair.get(pairKey);
            if (candidate == null) {
                throw new InvalidModelOutput(RejectionCategory.UNKNOWN_CANDIDATE);
            }
            int categoryCount = categoryCounts.merge(candidate.suggestion().category(), 1, Integer::sum);
            if (categoryCount > MAX_SUGGESTIONS_PER_CATEGORY) {
                throw new InvalidModelOutput(RejectionCategory.CATEGORY_OVERREPRESENTED);
            }
            selected.add(candidate.suggestion());
        }
        selected.sort(SUGGESTION_ORDER);
        return List.copyOf(selected);
    }

    private boolean validModelId(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_MODEL_ID_LENGTH;
    }

    private List<ReviewSuggestion> suggestionsFrom(List<ReviewCandidate> candidates) {
        return candidates.stream().map(ReviewCandidate::suggestion).toList();
    }

    private List<ReviewCandidate> diversifyCandidates(List<ReviewCandidate> candidates) {
        List<ReviewCandidate> diversified = new ArrayList<>();
        java.util.Map<String, Integer> categoryCounts = new java.util.HashMap<>();
        for (ReviewCandidate candidate : candidates) {
            String category = candidate.suggestion().category();
            if (categoryCounts.getOrDefault(category, 0) >= MAX_SUGGESTIONS_PER_CATEGORY) {
                continue;
            }
            diversified.add(candidate);
            categoryCounts.merge(category, 1, Integer::sum);
            if (diversified.size() == MAX_SUGGESTIONS) {
                break;
            }
        }
        return List.copyOf(diversified);
    }

    private boolean matches(ReviewKnowledgeBase.ReviewRule rule, String evidence) {
        String searchableEvidence = evidence.toLowerCase(Locale.ROOT);
        return rule.terms().stream().anyMatch(searchableEvidence::contains);
    }

    private List<Evidence> extractEvidence(String parsedJson) {
        try {
            JsonNode root = objectMapper.readTree(parsedJson);
            if (root == null || !root.isObject()) {
                throw new InvalidDocumentStateException();
            }
            List<Evidence> evidence = new ArrayList<>();
            int nextEvidenceId = 1;
            for (EvidenceField field : EVIDENCE_FIELDS) {
                JsonNode values = root.get(field.jsonField());
                if (values == null || !values.isArray()) {
                    throw new InvalidDocumentStateException();
                }
                for (JsonNode value : values) {
                    if (!value.isTextual() || value.asText().isBlank()) {
                        throw new InvalidDocumentStateException();
                    }
                    evidence.add(new Evidence("E" + nextEvidenceId++, field.category(), value.asText()));
                }
            }
            return evidence;
        } catch (InvalidDocumentStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidDocumentStateException();
        }
    }

    private static int priorityOrder(String priority) {
        return switch (priority) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private record EvidenceField(String jsonField, String category) {
    }

    private static String pairKey(String ruleId, String evidenceId) {
        return ruleId + '\u0000' + evidenceId;
    }

    private record Evidence(String id, String category, String text) {
    }

    private record ReviewCandidate(
            String ruleId,
            String evidenceId,
            ReviewSuggestion suggestion,
            ResumeReviewModelCandidate modelCandidate
    ) {
        private String pairKey() {
            return ResumeReviewService.pairKey(ruleId, evidenceId);
        }
    }

    private record ModelSelectionResponse(List<ModelSelection> suggestions) {
    }

    private record ModelSelection(String ruleId, String evidenceId) {
    }

    private static class InvalidModelOutput extends RuntimeException {

        private final RejectionCategory category;

        private InvalidModelOutput(RejectionCategory category) {
            this.category = category;
        }

        private RejectionCategory category() {
            return category;
        }
    }

    private enum RejectionCategory {
        MALFORMED_OUTPUT,
        INVALID_SELECTION_COUNT,
        INVALID_IDENTIFIER,
        DUPLICATE_PAIR,
        UNKNOWN_CANDIDATE,
        CATEGORY_OVERREPRESENTED
    }
}
