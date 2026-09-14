package com.hermanli.careerpilot.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermanli.careerpilot.analysis.MatchReport;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Service
public class PlanGenerationService {

    public static final int MAXIMUM_ACTIVE_TASKS = 8;
    private static final String INVALID_PLAN_MESSAGE = "The plan could not be generated. Please try again.";
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanGenerationService.class);

    private final PlanGenerator planGenerator;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public PlanGenerationService(PlanGenerator planGenerator, ObjectMapper objectMapper, Validator validator) {
        this.planGenerator = planGenerator;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public PlanDraft generate(MatchReport report, String jobDescriptionParsedJson) {
        return generate(report, jobDescriptionParsedJson, "[]", List.of(), MAXIMUM_ACTIVE_TASKS);
    }

    public PlanDraft generateRemaining(MatchReport report, String jobDescriptionParsedJson, List<PlanTask> priorTasks,
                                       int maximumTasks) {
        try {
            return generate(report, jobDescriptionParsedJson, objectMapper.writeValueAsString(priorTasks), priorTasks,
                    maximumTasks);
        } catch (JsonProcessingException exception) {
            throw new InvalidPlanException();
        }
    }

    public PlanDraft generateSafeFallback(MatchReport report, String jobDescriptionParsedJson) {
        Map<String, Evidence> allowedEvidence = allowedEvidence(report, jobDescriptionParsedJson);
        NormalizedGapRequest request = normalizedGaps(allowedEvidence, List.of(), MAXIMUM_ACTIVE_TASKS);
        if (request.gaps().isEmpty()) {
            throw new InvalidPlanException();
        }
        List<PlanTaskDraft> tasks = java.util.stream.IntStream.range(0, request.taskLimit())
                .mapToObj(index -> fallbackTask(request.gaps().get(index), index + 1))
                .toList();
        PlanDraft fallback = new PlanDraft(
                "14-Day Evidence Verification Plan",
                "A safe evidence-based plan created after the generated plan did not pass validation.",
                tasks
        );
        if (!validator.validate(fallback).isEmpty()) {
            throw new InvalidPlanException();
        }
        LOGGER.warn("Using deterministic evidence-based preparation plan fallback");
        return fallback;
    }

    private PlanTaskDraft fallbackTask(NormalizedGap gap, int dayOffset) {
        String label = java.util.Arrays.stream(gap.focusArea().toLowerCase(Locale.ROOT).split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
        String sourceEvidence = gap.positiveEvidence().stream()
                .max(Comparator.comparingInt(String::length))
                .orElse(gap.gapEvidence());
        String description = gap.positiveEvidence().isEmpty()
                ? "Verify whether any real resume, project, coursework, or work evidence supports " + label
                + ". Record the result without adding unsupported claims."
                : "Map and verify the existing evidence for " + label
                + " against the job requirement without adding unsupported tools, incidents, metrics, or outcomes.";
        return new PlanTaskDraft(
                "Verify " + label + " evidence",
                description,
                Math.min(dayOffset, 14),
                gap.priority(),
                sourceEvidence,
                gap.focusArea(),
                "EVIDENCE_VERIFICATION",
                "One verified evidence-to-requirement mapping with a yes/no conclusion"
        );
    }

    private PlanDraft generate(MatchReport report, String jobDescriptionParsedJson, String priorTaskProgressJson,
                               List<PlanTask> priorTasks, int maximumTasks) {
        try {
            if (maximumTasks < 1 || maximumTasks > MAXIMUM_ACTIVE_TASKS) {
                throw new InvalidPlanException();
            }
            Map<String, Evidence> allowedEvidence = allowedEvidence(report, jobDescriptionParsedJson);
            NormalizedGapRequest normalizedGaps = normalizedGaps(allowedEvidence, priorTasks, maximumTasks);
            if (normalizedGaps.gaps().isEmpty()) {
                throw new InvalidPlanException();
            }
            String generatedPlan = planGenerator.generate(
                    report, jobDescriptionParsedJson, priorTaskProgressJson,
                    objectMapper.writeValueAsString(normalizedGaps)
            );
            PlanDraft draft = objectMapper.readerFor(PlanDraft.class)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .readValue(removeCodeFence(generatedPlan));
            if (!validator.validate(draft).isEmpty() || draft.tasks().size() > maximumTasks) {
                throw new InvalidPlanException();
            }
            return validateAndCanonicalize(draft, allowedEvidence, priorTasks, normalizedGaps);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Preparation plan output rejected: INVALID_JSON");
            throw new InvalidPlanException();
        } catch (InvalidPlanException exception) {
            LOGGER.warn("Preparation plan output rejected: SEMANTIC_VALIDATION");
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn("Preparation plan output rejected: GENERATOR_OR_VALIDATION_RUNTIME");
            throw new InvalidPlanException();
        }
    }

    private PlanDraft validateAndCanonicalize(PlanDraft draft, Map<String, Evidence> allowedEvidence,
                                              List<PlanTask> priorTasks, NormalizedGapRequest normalizedGaps) {
        Set<String> focusAreas = new HashSet<>();
        Set<String> priorTitles = new HashSet<>();
        Set<String> protectedFocusAreas = protectedFocusAreas(priorTasks);
        Map<String, NormalizedGap> gapsByFocusArea = normalizedGaps.byFocusArea();
        priorTasks.forEach(task -> priorTitles.add(normalize(task.title())));

        List<PlanTaskDraft> tasks = draft.tasks().stream().map(task -> {
            Evidence requestedEvidence = allowedEvidence.get(normalize(task.sourceEvidence()));
            NormalizedGap gap = gapsByFocusArea.get(task.focusArea());
            if (requestedEvidence == null || gap == null || priorTitles.contains(normalize(task.title()))
                    || !gap.containsEvidence(requestedEvidence.value())) {
                throw new InvalidPlanException();
            }
            Evidence evidence = resolveEvidence(task, requestedEvidence, gap, allowedEvidence);
            if (!focusAreas.add(task.focusArea())
                    || protectedFocusAreas.contains(task.focusArea())) {
                throw new InvalidPlanException();
            }
            if (Set.of("RESUME_APPLICATION", "INTERVIEW_STORY").contains(task.taskType())
                    && (!evidence.supportsExperienceClaim() || !gap.positiveEvidence().contains(evidence.value()))) {
                throw new InvalidPlanException();
            }
            validateEvidenceAwareSelection(task, evidence, gap);
            if ("TDD".equals(task.focusArea()) && Set.of("RESUME_APPLICATION", "INTERVIEW_STORY").contains(task.taskType())
                    && !isConfirmedTddEvidence(evidence)) {
                throw new InvalidPlanException();
            }
            if ("CLOUD_COMPUTING".equals(task.focusArea()) && "RESUME_APPLICATION".equals(task.taskType())
                    && !hasConfirmedCloudPlatform(evidence)) {
                throw new InvalidPlanException();
            }
            return new PlanTaskDraft(
                    task.title(), task.description(), task.dayOffset(), calibratedPriority(task, evidence),
                    evidence.value(), task.focusArea(), task.taskType(), task.deliverable()
            );
        }).toList();
        return new PlanDraft(draft.title(), draft.summary(), tasks);
    }

    private Evidence resolveEvidence(PlanTaskDraft task, Evidence requestedEvidence, NormalizedGap gap,
                                     Map<String, Evidence> allowedEvidence) {
        if (!Set.of("RESUME_APPLICATION", "INTERVIEW_STORY").contains(task.taskType())) {
            return requestedEvidence;
        }
        String taskText = normalize(task.title() + " " + task.description() + " " + task.deliverable());
        if (isUsableClaimEvidence(task, taskText, requestedEvidence, gap)) {
            return requestedEvidence;
        }
        return gap.positiveEvidence().stream()
                .map(value -> allowedEvidence.get(normalize(value)))
                .filter(java.util.Objects::nonNull)
                .filter(evidence -> isUsableClaimEvidence(task, taskText, evidence, gap))
                .max(Comparator.comparingInt(evidence -> evidence.value().length()))
                .orElseThrow(InvalidPlanException::new);
    }

    private boolean isUsableClaimEvidence(PlanTaskDraft task, String taskText, Evidence evidence, NormalizedGap gap) {
        if (!evidence.supportsExperienceClaim() || !gap.positiveEvidence().contains(evidence.value())
                || containsUnsupportedDetails(task.taskType(), taskText, evidence.value())) {
            return false;
        }
        if (!"INTERVIEW_STORY".equals(task.taskType())) {
            return true;
        }
        if (!hasConcreteExperienceEvidence(evidence.value())) {
            return false;
        }
        return !Set.of("TROUBLESHOOTING", "PROBLEM_SOLVING").contains(task.focusArea())
                || hasVerifiedIncidentEvidence(evidence.value());
    }

    private void validateEvidenceAwareSelection(PlanTaskDraft task, Evidence evidence, NormalizedGap gap) {
        if (!gap.allowedTaskTypes().contains(task.taskType())) {
            throw new InvalidPlanException();
        }
        String taskText = normalize(task.title() + " " + task.description() + " " + task.deliverable());
        if ("INTERVIEW_STORY".equals(task.taskType())
                && (gap.evidenceStrength() == EvidenceStrength.NONE || containsAny(taskText,
                "hypothetical", "fictional", "made-up", "made up", "imagine a", "suppose that", "sample scenario")
                || !hasConcreteExperienceEvidence(evidence.value()))) {
            throw new InvalidPlanException();
        }
        validateTaskTypeAction(task.taskType(), taskText);
        validateUnsupportedDetails(task.taskType(), taskText, evidence.value());
        if (requestsQuantifiedOutcome(taskText)
                && gap.positiveEvidence().stream().noneMatch(this::hasQuantifiedOutcomeEvidence)) {
            throw new InvalidPlanException();
        }
        if (!"NONE".equals(gap.capabilityFloor()) && isBasicTask(task.focusArea(), taskText)) {
            throw new InvalidPlanException();
        }
        if ("INTERVIEW_STORY".equals(task.taskType())
                && Set.of("TROUBLESHOOTING", "PROBLEM_SOLVING").contains(task.focusArea())
                && !hasVerifiedIncidentEvidence(evidence.value())) {
            throw new InvalidPlanException();
        }
    }

    private void validateTaskTypeAction(String taskType, String taskText) {
        boolean learningAction = containsAny(taskText, "learn ", "study ", "fundamentals", "basics",
                "theory", "iaas", "paas", "saas", "shared responsibility", "tuckman");
        boolean verificationAction = containsAny(taskText, "verify ", "confirm ", "check whether", "determine whether",
                "identify whether", "identify and verify", "map ", "trace ");
        if ("EVIDENCE_VERIFICATION".equals(taskType) && (learningAction || !verificationAction)) {
            throw new InvalidPlanException();
        }
        if ("CONCEPT_LEARNING".equals(taskType) && (!learningAction || containsAny(taskText,
                "verify whether", "confirm whether", "check whether", "identify and verify"))) {
            throw new InvalidPlanException();
        }
    }

    private void validateUnsupportedDetails(String taskType, String taskText, String sourceEvidence) {
        if (containsUnsupportedDetails(taskType, taskText, sourceEvidence)) {
            throw new InvalidPlanException();
        }
    }

    private boolean containsUnsupportedDetails(String taskType, String taskText, String sourceEvidence) {
        if ("CONCEPT_LEARNING".equals(taskType)) {
            return false;
        }
        String source = normalize(sourceEvidence);
        if (hasAbsentDetail(taskText, source,
                "thread pool", "connection leak", "response timing", "docker logs", "browser dev tools",
                "browser developer tools", "memory leak", "deadlock", "race condition", "database bottleneck",
                "production outage", "inconsistent api response", "failed deployment", "improved performance",
                "reduced latency", "increased throughput", "saved time")) {
            return true;
        }
        if (Set.of("RESUME_APPLICATION", "INTERVIEW_STORY").contains(taskType)
                || containsAny(taskText, "using postman", "using docker", "using playwright", "using junit",
                "using jest", "using nginx", "using kubernetes", "using redis")) {
            return hasAbsentDetail(taskText, source,
                    "postman", "docker", "playwright", "junit", "jest", "github actions", "nginx", "kubernetes",
                    "redis", "aws", "azure", "gcp", "digitalocean", "react", "spring boot", "mysql",
                    "postgresql", "mongodb", "rest api", "microservices", "event-driven architecture",
                    "layered architecture", "monolith");
        }
        return false;
    }

    private boolean hasAbsentDetail(String taskText, String sourceEvidence, String... details) {
        for (String detail : details) {
            if (taskText.contains(detail) && !sourceEvidence.contains(detail)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConcreteExperienceEvidence(String evidence) {
        return containsAny(normalize(evidence), "built ", "implemented ", "developed ", "created ", "designed ",
                "mapped ", "used ", "deployed ", "automated ", "worked ", "collaborated ", "resolved ", "fixed ",
                "debugged ", "diagnosed ", "project", "experience");
    }

    private boolean hasVerifiedIncidentEvidence(String evidence) {
        return containsAny(normalize(evidence), "resolved ", "fixed ", "debugged ", "diagnosed ", "troubleshot ",
                "incident", "failure", "failed ", "bug", "error");
    }

    private NormalizedGapRequest normalizedGaps(Map<String, Evidence> allowedEvidence, List<PlanTask> priorTasks,
                                                int maximumTasks) {
        Map<String, GapCandidate> candidates = new LinkedHashMap<>();
        allowedEvidence.values().forEach(evidence -> {
            String focusArea = canonicalFocusArea(evidence.value());
            if (focusArea == null || evidence.kind().supportsExperienceClaim()) {
                return;
            }
            if ("PROGRAMMING".equals(focusArea) && isProfessionalExperienceOnlyGap(evidence.value())) {
                return;
            }
            GapCandidate candidate = new GapCandidate(focusArea, evidence);
            candidates.merge(focusArea, candidate, GapCandidate::preferHigherSignal);
        });
        Set<String> protectedFocusAreas = protectedFocusAreas(priorTasks);
        List<NormalizedGap> gaps = candidates.values().stream()
                .filter(candidate -> !protectedFocusAreas.contains(candidate.focusArea()))
                .map(candidate -> normalizedGap(candidate, allowedEvidence.values().stream().toList()))
                .sorted(Comparator.comparingInt((NormalizedGap gap) -> priorityRank(gap.priority())).reversed()
                        .thenComparing(NormalizedGap::focusArea))
                .toList();
        return new NormalizedGapRequest(Math.min(maximumTasks, gaps.size()), gaps);
    }

    private String removeCodeFence(String generatedPlan) {
        if (generatedPlan == null || generatedPlan.isBlank()) {
            throw new InvalidPlanException();
        }
        String trimmed = generatedPlan.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0 || !trimmed.endsWith("```")) {
            throw new InvalidPlanException();
        }
        return trimmed.substring(firstLineEnd + 1, trimmed.length() - 3).trim();
    }

    private NormalizedGap normalizedGap(GapCandidate candidate, List<Evidence> allEvidence) {
        List<Evidence> directPositive = matchingEvidence(allEvidence,
                evidence -> evidence.supportsExperienceClaim()
                        && candidate.focusArea().equals(canonicalFocusArea(evidence.value())));
        List<Evidence> relatedPositive = matchingEvidence(allEvidence,
                evidence -> evidence.supportsExperienceClaim()
                        && supportsRelatedFocus(candidate.focusArea(), evidence.value()));
        List<Evidence> positive = java.util.stream.Stream.concat(directPositive.stream(), relatedPositive.stream())
                .collect(java.util.stream.Collectors.toMap(
                        evidence -> normalize(evidence.value()), evidence -> evidence, (first, ignored) -> first,
                        LinkedHashMap::new))
                .values().stream().toList();
        EvidenceStrength strength = directPositive.stream().anyMatch(e -> e.kind() == EvidenceKind.POSITIVE)
                ? EvidenceStrength.STRONG
                : positive.stream().anyMatch(e -> e.kind() == EvidenceKind.PARTIAL) || !relatedPositive.isEmpty()
                ? EvidenceStrength.PARTIAL : EvidenceStrength.NONE;
        String capabilityFloor = capabilityFloor(candidate.focusArea(), positive);
        return new NormalizedGap(
                candidate.focusArea(), candidate.evidence().value(), canonicalPriority(candidate.focusArea()),
                strength, capabilityFloor, allowedTaskTypes(strength), positive.stream().map(Evidence::value).toList()
        );
    }

    private List<Evidence> matchingEvidence(List<Evidence> evidence, Predicate<Evidence> predicate) {
        return evidence.stream().filter(predicate).toList();
    }

    private boolean supportsRelatedFocus(String focusArea, String evidence) {
        String value = normalize(evidence);
        return switch (focusArea) {
            case "PROGRAMMING" -> containsAny(value, "spring boot", "full stack", "full-stack", "java project",
                    "typescript", "react", "mysql");
            case "REQUIREMENTS_ANALYSIS" -> containsAny(value, "user need", "workflow", "data model", "authorization",
                    "business rule", "api contract", "acceptance criteria", "deadline", "follow-up", "follow up",
                    "status history", "application tracker");
            case "TROUBLESHOOTING" -> containsAny(value, "fixed", "resolved", "debug", "test suite", "junit", "jest",
                    "playwright", "deployment", "docker", "github actions", "ci/cd");
            case "INTEGRATION" -> containsAny(value, "rest api", "restful", "endpoint", "controller", "service layer",
                    "frontend", "database");
            case "SDLC" -> containsAny(value, "git ", "github", "github actions", "automated test", "automated build",
                    "ci/cd", "release", "branch", "pull request");
            case "COLLABORATION" -> containsAny(value, "team", "worked with", "collaborated", "group project",
                    "cross-functional", "pair programming");
            default -> false;
        };
    }

    private List<String> allowedTaskTypes(EvidenceStrength strength) {
        return switch (strength) {
            case STRONG -> List.of("RESUME_APPLICATION", "INTERVIEW_STORY", "EVIDENCE_VERIFICATION");
            case PARTIAL -> List.of("EVIDENCE_VERIFICATION", "RESUME_APPLICATION", "INTERVIEW_STORY");
            case NONE -> List.of("EVIDENCE_VERIFICATION", "CONCEPT_LEARNING");
        };
    }

    private String capabilityFloor(String focusArea, List<Evidence> positiveEvidence) {
        String evidence = normalize(positiveEvidence.stream().map(Evidence::value)
                .collect(java.util.stream.Collectors.joining(" ")));
        if ("PROGRAMMING".equals(focusArea) && containsAny(evidence, "spring boot", "rest api", "full stack",
                "full-stack", "mysql", "playwright", "github actions")) {
            return "ADVANCED";
        }
        if ("INTEGRATION".equals(focusArea) && containsAny(evidence, "rest api", "restful", "endpoint", "controller")) {
            return "APPLIED";
        }
        if (!positiveEvidence.isEmpty()) {
            return "ESTABLISHED";
        }
        return "NONE";
    }

    private boolean isBasicTask(String focusArea, String taskText) {
        if ("PROGRAMMING".equals(focusArea)) {
            return containsAny(taskText, "variables and loops", "variables, loops", "conditionals and functions",
                    "basic programming", "programming basics", "programming fundamentals", "fundamentals quiz",
                    "hello world");
        }
        if ("INTEGRATION".equals(focusArea)) {
            return containsAny(taskText, "what is rest", "define rest", "rest api basics", "api fundamentals");
        }
        return false;
    }

    private boolean requestsQuantifiedOutcome(String taskText) {
        return containsAny(taskText, "quantified outcome", "quantify the outcome", "add a metric", "include a metric",
                "quantified result", "quantified improvement", "percentage improvement", "time saved",
                "number of users", "measurable result");
    }

    private boolean hasQuantifiedOutcomeEvidence(String evidence) {
        String value = normalize(evidence);
        return value.contains("%") || value.matches(".*\\b(reduced|increased|improved|saved|decreased)\\b.{0,30}\\d+.*");
    }

    private boolean isProfessionalExperienceOnlyGap(String evidence) {
        String value = normalize(evidence);
        return containsAny(value, "no professional software", "lack of professional software",
                "no professional development experience", "no professional work experience");
    }

    private Set<String> protectedFocusAreas(List<PlanTask> priorTasks) {
        Set<String> focusAreas = new HashSet<>();
        priorTasks.stream()
                .filter(task -> Set.of("COMPLETED", "SKIPPED", "IN_PROGRESS").contains(task.status()))
                .map(task -> task.title() + " " + task.description() + " " + task.sourceEvidence())
                .map(PlanGenerationService::canonicalFocusArea)
                .filter(java.util.Objects::nonNull)
                .forEach(focusAreas::add);
        return focusAreas;
    }

    private String calibratedPriority(PlanTaskDraft task, Evidence evidence) {
        String focusArea = task.focusArea();
        if ("MICROSERVICES".equals(focusArea) && "CONCEPT_LEARNING".equals(task.taskType())) {
            return "LOW";
        }
        if ("TDD".equals(focusArea) && !isConfirmedTddEvidence(evidence)) {
            return "LOW";
        }
        if (Set.of("REQUIREMENTS_ANALYSIS", "CLOUD_COMPUTING", "TROUBLESHOOTING").contains(focusArea)
                && Set.of("RESUME_APPLICATION", "INTERVIEW_STORY", "EVIDENCE_VERIFICATION").contains(task.taskType())) {
            return "HIGH";
        }
        if (Set.of("PROBLEM_SOLVING", "COMMUNICATION", "LEARNING_AGILITY").contains(focusArea)) {
            return "MEDIUM";
        }
        if ("CONCEPT_LEARNING".equals(task.taskType())) {
            return "LOW";
        }
        if (evidence.kind() == EvidenceKind.PARTIAL) {
            return "MEDIUM";
        }
        return task.priority();
    }

    private String canonicalPriority(String focusArea) {
        if (Set.of("REQUIREMENTS_ANALYSIS", "CLOUD_COMPUTING", "TROUBLESHOOTING").contains(focusArea)) {
            return "HIGH";
        }
        if (Set.of("PROBLEM_SOLVING", "COMMUNICATION", "LEARNING_AGILITY").contains(focusArea)) {
            return "MEDIUM";
        }
        if (Set.of("TDD", "MICROSERVICES").contains(focusArea)) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private int priorityRank(String priority) {
        return switch (priority) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private Map<String, Evidence> allowedEvidence(MatchReport report, String jobDescriptionParsedJson) {
        Map<String, Evidence> evidence = new LinkedHashMap<>();
        addEvidence(evidence, report.matchedSkills(), EvidenceKind.POSITIVE);
        addEvidence(evidence, report.strengths(), EvidenceKind.POSITIVE);
        addEvidence(evidence, report.partialMatches(), EvidenceKind.PARTIAL);
        addEvidence(evidence, report.missingSkills(), EvidenceKind.GAP);
        addEvidence(evidence, report.risks(), EvidenceKind.GAP);
        addEvidence(evidence, report.recommendations(), EvidenceKind.RECOMMENDATION);
        try {
            collectTextValues(objectMapper.readTree(jobDescriptionParsedJson), evidence);
        } catch (JsonProcessingException exception) {
            throw new InvalidPlanException();
        }
        return evidence;
    }

    private void addEvidence(Map<String, Evidence> evidence, List<String> values, EvidenceKind kind) {
        values.forEach(value -> evidence.putIfAbsent(normalize(value), new Evidence(value, kind)));
    }

    private void collectTextValues(JsonNode node, Map<String, Evidence> evidence) {
        if (node.isTextual()) {
            String value = node.asText();
            evidence.putIfAbsent(normalize(value), new Evidence(value, EvidenceKind.JOB_DESCRIPTION));
            return;
        }
        node.elements().forEachRemaining(child -> collectTextValues(child, evidence));
    }

    static String canonicalFocusArea(String evidence) {
        String value = normalize(evidence);
        if (containsAny(value, "test driven", "test-driven", "tdd")) return "TDD";
        if (containsAny(value, "requirements analysis", "business requirement", "technical requirement",
                "functional requirement", "non-functional requirement", "requirements definition",
                "requirements evidence", "requirements language", "stakeholder", "user need", "client need",
                "technical specification")) return "REQUIREMENTS_ANALYSIS";
        if (containsAny(value, "cloud", "aws", "azure", "gcp", "digitalocean", "cloud-hosted")) return "CLOUD_COMPUTING";
        if (containsAny(value, "microservice")) return "MICROSERVICES";
        if (containsAny(value, "troubleshoot", "debug", "fault rectification")) return "TROUBLESHOOTING";
        if (containsAny(value, "problem solving", "problem-solving", "diagnos", "solve problems")) return "PROBLEM_SOLVING";
        if (containsAny(value, "written communication", "verbal communication", "communicat", "documentation")) return "COMMUNICATION";
        if (containsAny(value, "collaborat", "teamwork", "team work")) return "COLLABORATION";
        if (containsAny(value, "adaptab", "learning agility", "learn quickly", "upskill")) return "LEARNING_AGILITY";
        if (containsAny(value, "devops", "delivery", "ci/cd", "continuous integration", "github actions", "docker", "nginx")) return "DEVOPS_DELIVERY";
        if (containsAny(value, "quality assurance", "testing", "test suite", "junit", "jest", "playwright")) return "TESTING";
        if (containsAny(value, "integration", "rest api", "restful", "build api", "endpoint")) return "INTEGRATION";
        if (containsAny(value, "sdlc", "software development lifecycle", "release", "maintenance")) return "SDLC";
        if (containsAny(value, "programming", "java", "spring boot", "react", "typescript", "software development")) return "PROGRAMMING";
        return null;
    }

    private boolean isConfirmedTddEvidence(Evidence evidence) {
        String value = normalize(evidence.value());
        return evidence.kind() == EvidenceKind.POSITIVE
                && containsAny(value, "test driven", "test-driven", "tdd")
                && containsAny(value, "before implementation", "failing test", "red-green-refactor");
    }

    private boolean hasConfirmedCloudPlatform(Evidence evidence) {
        return evidence.supportsExperienceClaim()
                && containsAny(normalize(evidence.value()), "aws", "azure", "gcp", "digitalocean", "cloud-hosted");
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private enum EvidenceKind {
        POSITIVE, PARTIAL, GAP, RECOMMENDATION, JOB_DESCRIPTION;

        private boolean supportsExperienceClaim() {
            return this == POSITIVE || this == PARTIAL;
        }
    }

    private record Evidence(String value, EvidenceKind kind) {
        private boolean supportsExperienceClaim() {
            return kind.supportsExperienceClaim();
        }
    }

    private record GapCandidate(String focusArea, Evidence evidence) {
        private static GapCandidate preferHigherSignal(GapCandidate first, GapCandidate second) {
            return signal(first.evidence().kind()) >= signal(second.evidence().kind()) ? first : second;
        }

        private static int signal(EvidenceKind kind) {
            return switch (kind) {
                case GAP -> 3;
                case RECOMMENDATION -> 2;
                case JOB_DESCRIPTION -> 1;
                default -> 0;
            };
        }
    }

    private enum EvidenceStrength {
        STRONG, PARTIAL, NONE
    }

    private record NormalizedGap(String focusArea, String gapEvidence, String priority,
                                 EvidenceStrength evidenceStrength, String capabilityFloor,
                                 List<String> allowedTaskTypes, List<String> positiveEvidence) {
        private boolean containsEvidence(String value) {
            return gapEvidence.equals(value) || positiveEvidence.contains(value);
        }
    }

    private record NormalizedGapRequest(int taskLimit, List<NormalizedGap> gaps) {
        private Map<String, NormalizedGap> byFocusArea() {
            return gaps.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    NormalizedGap::focusArea, gap -> gap));
        }
    }

    public static class InvalidPlanException extends RuntimeException {
        public InvalidPlanException() {
            super(INVALID_PLAN_MESSAGE);
        }
    }
}
