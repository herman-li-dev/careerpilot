package com.hermanli.careerpilot.analysis;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

enum ScoredCapability {

    PROGRAMMING(
            "Programming",
            false,
            Set.of("programming", "software development", "software program", "software programs", "java", "javascript", "typescript", "python", "c++", "sql", "spring boot", "react"),
            Set.of("java", "javascript", "typescript", "python", "c++", "sql", "spring boot", "react")
    ),
    TESTING_QA(
            "Quality Assurance and Testing",
            false,
            Set.of("quality assurance", "testing", "software test"),
            Set.of("junit", "jest", "playwright", "postman", "quality assurance", "testing")
    ),
    TROUBLESHOOTING(
            "Troubleshooting",
            false,
            Set.of("troubleshooting", "fault rectification", "corrective maintenance"),
            Set.of("troubleshooting", "debugging", "diagnosed", "resolved", "fixed")
    ),
    SDLC(
            "System Development Lifecycle",
            false,
            Set.of("system development lifecycle", "development lifecycle", "security", "logging", "error handling", "performance standards"),
            Set.of("system development lifecycle", "github actions", "maven", "git", "testing", "ci/cd")
    ),
    APPLICATION_INTEGRATION(
            "Application and System Integration",
            false,
            Set.of("applications integration", "application integration", "system and technology integration", "system integration", "upstream", "downstream impacts"),
            Set.of("rest api", "rest apis", "spring mvc", "integration", "mysql", "redis")
    ),
    REQUIREMENTS_ANALYSIS(
            "Requirements Analysis",
            true,
            Set.of("requirements definition", "requirements analysis", "client requirements", "user requirements", "technical specifications", "requirements mapping"),
            Set.of("requirements", "user needs", "technical specifications", "stakeholder", "workflow")
    ),
    COMMUNICATION(
            "Verbal and Written Communication",
            true,
            Set.of("verbal and written communication", "written communication", "verbal communication", "communicate"),
            Set.of("communicated", "explaining", "guided customers", "customer", "service requests", "product value", "documentation")
    ),
    COLLABORATION(
            "Collaboration and Teamwork",
            true,
            Set.of("collaboration", "team skills", "teamwork", "work with a team"),
            Set.of("collaborated", "team members", "team", "pair programming", "code review")
    ),
    PROBLEM_SOLVING(
            "Analytical and Problem Solving",
            true,
            Set.of("problem solving", "creative thinking", "think creatively", "exercise judgment", "diagnose and solve problems", "new solutions"),
            Set.of("solved", "designed", "analytics", "workflow", "data models", "filtering", "authorization", "diagnosed")
    ),
    ADAPTABILITY_LEARNING(
            "Adaptability and Learning Agility",
            true,
            Set.of("adaptability", "learning agility", "learn new", "independently"),
            Set.of("learned", "self-directed", "independently", "adapted", "multiple languages")
    ),
    CLOUD(
            "Cloud Computing",
            false,
            Set.of("cloud computing", "cloud platform", "aws", "azure", "google cloud"),
            Set.of("cloud computing", "aws", "azure", "google cloud", "gcp")
    ),
    MICROSERVICES(
            "Microservices",
            false,
            Set.of("microservices", "microservice"),
            Set.of("microservices", "microservice")
    ),
    TDD(
            "Test Driven Development",
            false,
            Set.of("test driven development", "tdd"),
            Set.of("test driven development", "tdd")
    ),
    DEVOPS_DELIVERY(
            "DevOps and Software Delivery",
            false,
            Set.of("docker", "ci/cd", "continuous integration", "continuous delivery", "release management", "version control", "deployment"),
            Set.of("docker", "github actions", "ci/cd", "continuous integration", "continuous delivery", "git", "nginx", "linux", "deployment")
    );

    private final String label;
    private final boolean relatedEvidenceIsPartial;
    private final Set<String> jobAliases;
    private final Set<String> resumeAliases;

    ScoredCapability(
            String label,
            boolean relatedEvidenceIsPartial,
            Set<String> jobAliases,
            Set<String> resumeAliases
    ) {
        this.label = label;
        this.relatedEvidenceIsPartial = relatedEvidenceIsPartial;
        this.jobAliases = jobAliases;
        this.resumeAliases = resumeAliases;
    }

    String label() {
        return label;
    }

    boolean relatedEvidenceIsPartial() {
        return relatedEvidenceIsPartial;
    }

    boolean requiredBy(Set<String> jobEvidence) {
        return containsAny(jobEvidence, jobAliases);
    }

    boolean supportedBy(Set<String> resumeEvidence) {
        return containsAny(resumeEvidence, resumeAliases);
    }

    boolean matchesReportItem(String item) {
        String normalized = normalize(item);
        return related(normalize(label), normalized)
                || jobAliases.stream().map(ScoredCapability::normalize).anyMatch(alias -> related(alias, normalized))
                || resumeAliases.stream().map(ScoredCapability::normalize).anyMatch(alias -> related(alias, normalized));
    }

    static Optional<ScoredCapability> fromReportItem(String item) {
        return Arrays.stream(values()).filter(capability -> capability.matchesReportItem(item)).findFirst();
    }

    private static boolean containsAny(Set<String> evidence, Set<String> aliases) {
        return aliases.stream().map(ScoredCapability::normalize)
                .anyMatch(alias -> evidence.stream().anyMatch(value -> related(alias, value)));
    }

    private static boolean related(String first, String second) {
        return first.equals(second) || containsPhrase(first, second) || containsPhrase(second, first);
    }

    private static boolean containsPhrase(String value, String phrase) {
        return (" " + value + " ").contains(" " + phrase + " ");
    }

    static String normalize(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replaceAll("[^a-z0-9+#/]+", " ")
                .replaceAll("\\s+", " ");
    }
}
