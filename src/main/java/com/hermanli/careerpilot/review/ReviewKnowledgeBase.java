package com.hermanli.careerpilot.review;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
class ReviewKnowledgeBase {

    private static final String RESOURCE_PATH = "careerpilot/review/synthetic-review-rules-v1.json";
    private static final int MAX_RESOURCE_BYTES = 16 * 1024;
    private static final int MAX_RULES = 12;
    private static final Set<String> CATEGORIES = Set.of(
            "SKILLS", "PROJECTS", "EXPERIENCE", "EDUCATION", "CREDENTIALS"
    );
    private static final Set<String> PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final ObjectMapper STRICT_OBJECT_MAPPER = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()
    );

    private final String version;
    private final List<ReviewRule> rules;

    ReviewKnowledgeBase() {
        this(loadBundledRules());
    }

    private ReviewKnowledgeBase(ParsedRules parsedRules) {
        this.version = parsedRules.version();
        this.rules = parsedRules.rules();
    }

    String version() {
        return version;
    }

    List<ReviewRule> rules() {
        return rules;
    }

    ReviewRule ruleForCategory(String category) {
        return rules.stream().filter(rule -> rule.category().equals(category)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Resume review knowledge is unavailable."));
    }

    static ParsedRules parseForTest(String content) {
        try {
            return parse(content);
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private static ParsedRules loadBundledRules() {
        try (InputStream input = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            byte[] bytes = input.readAllBytes();
            if (bytes.length == 0 || bytes.length > MAX_RESOURCE_BYTES) {
                throw new IOException("invalid review resource size");
            }
            return parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private static ParsedRules parse(String content) throws IOException {
        JsonNode root = STRICT_OBJECT_MAPPER.readTree(content);
        if (root == null || !root.isObject() || root.size() != 2
                || !root.has("version") || !root.has("rules")) {
            throw new IOException("invalid review resource");
        }
        String version = requiredText(root, "version", 64);
        JsonNode ruleNodes = root.get("rules");
        if (!ruleNodes.isArray() || ruleNodes.isEmpty() || ruleNodes.size() > MAX_RULES) {
            throw new IOException("invalid review rules");
        }
        Set<String> ids = new HashSet<>();
        List<ReviewRule> parsedRules = new ArrayList<>();
        for (JsonNode ruleNode : ruleNodes) {
            parsedRules.add(parseRule(ruleNode, ids));
        }
        return new ParsedRules(version, List.copyOf(parsedRules));
    }

    private static ReviewRule parseRule(JsonNode node, Set<String> ids) throws IOException {
        if (!node.isObject() || node.size() != 7) {
            throw new IOException("invalid review rule");
        }
        String id = requiredText(node, "id", 60);
        if (!id.matches("[a-z0-9-]{3,60}") || !ids.add(id)) {
            throw new IOException("invalid review rule id");
        }
        String category = requiredText(node, "category", 24);
        String priority = requiredText(node, "priority", 12);
        if (!CATEGORIES.contains(category) || !PRIORITIES.contains(priority)) {
            throw new IOException("invalid review rule classification");
        }
        String sourceId = requiredText(node, "sourceId", 80);
        String sourceTitle = requiredText(node, "sourceTitle", 140);
        String recommendation = requiredText(node, "recommendation", 360);
        JsonNode termsNode = node.get("terms");
        if (!termsNode.isArray() || termsNode.isEmpty() || termsNode.size() > 8) {
            throw new IOException("invalid review rule terms");
        }
        Set<String> terms = new HashSet<>();
        for (JsonNode termNode : termsNode) {
            if (!termNode.isTextual() || termNode.asText().isBlank() || termNode.asText().length() > 64) {
                throw new IOException("invalid review term");
            }
            terms.add(termNode.asText().toLowerCase(Locale.ROOT));
        }
        if (terms.size() != termsNode.size()) {
            throw new IOException("duplicate review term");
        }
        return new ReviewRule(id, category, priority, sourceId, sourceTitle, List.copyOf(terms), recommendation);
    }

    private static String requiredText(JsonNode node, String field, int maxLength) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) {
            throw new IOException("invalid review field");
        }
        return value.asText();
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Resume review knowledge is unavailable.");
    }

    record ParsedRules(String version, List<ReviewRule> rules) {
    }

    record ReviewRule(
            String id,
            String category,
            String priority,
            String sourceId,
            String sourceTitle,
            List<String> terms,
            String recommendation
    ) {
    }
}
