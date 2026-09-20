package com.hermanli.careerpilot.review;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads the only document that is eligible for public resume-review retrieval.
 * The deliberately small parser keeps chunk boundaries stable across restarts.
 */
@Component
class SyntheticReviewGuide {

    static final String RESOURCE_PATH = "careerpilot/review/synthetic-review-guide-v1.md";
    static final String SOURCE_ID = "careerpilot-synthetic-resume-review-v1";
    static final String SOURCE_TITLE = "CareerPilot synthetic resume review guide";
    static final String SOURCE_VERSION = "synthetic-review-guide-v1";
    static final String INDEX_VERSION = "synthetic-review-rag-v1";
    private static final int MAX_RESOURCE_BYTES = 16 * 1024;
    private static final Set<String> CATEGORIES = Set.of("SKILLS", "PROJECTS", "EXPERIENCE", "EDUCATION");

    private final List<GuideChunk> chunks;

    SyntheticReviewGuide() {
        this(loadBundledGuide());
    }

    private SyntheticReviewGuide(List<GuideChunk> chunks) {
        this.chunks = chunks;
    }

    List<GuideChunk> chunks() {
        return chunks;
    }

    static List<GuideChunk> parseForTest(String content) {
        try {
            return parse(content);
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private static List<GuideChunk> loadBundledGuide() {
        try (InputStream input = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            byte[] bytes = input.readAllBytes();
            if (bytes.length == 0 || bytes.length > MAX_RESOURCE_BYTES) {
                throw new IOException("invalid guide resource size");
            }
            return parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private static List<GuideChunk> parse(String source) throws IOException {
        String normalized = source.replace("\r\n", "\n").trim();
        if (!normalized.startsWith("# CareerPilot synthetic resume review guide\n")) {
            throw new IOException("invalid guide title");
        }
        String[] sections = normalized.split("\n## ");
        if (sections.length != 5) {
            throw new IOException("invalid guide sections");
        }
        List<GuideChunk> result = new ArrayList<>();
        java.util.HashSet<String> categories = new java.util.HashSet<>();
        for (int index = 1; index < sections.length; index++) {
            String section = sections[index].trim();
            int newline = section.indexOf('\n');
            if (newline <= 0) {
                throw new IOException("invalid guide section");
            }
            String heading = section.substring(0, newline).trim();
            String[] headingParts = heading.split(": ", 2);
            if (headingParts.length != 2 || !CATEGORIES.contains(headingParts[0]) || headingParts[1].isBlank()
                    || headingParts[1].length() > 80 || !categories.add(headingParts[0])) {
                throw new IOException("invalid guide heading");
            }
            String content = section.substring(newline + 1).trim();
            if (content.isBlank() || content.length() > 600) {
                throw new IOException("invalid guide content");
            }
            int chunkIndex = index - 1;
            String contentHash = sha256(content);
            String id = java.util.UUID.nameUUIDFromBytes((INDEX_VERSION + "\u0000" + contentHash)
                    .getBytes(StandardCharsets.UTF_8)).toString();
            result.add(new GuideChunk(id, headingParts[0], headingParts[1], chunkIndex, content, contentHash));
        }
        if (!categories.equals(CATEGORIES)) {
            throw new IOException("missing guide category");
        }
        return List.copyOf(result);
    }

    static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(Locale.ROOT, "%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    static Map<String, Object> metadata(GuideChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceId", SOURCE_ID);
        metadata.put("sourceTitle", SOURCE_TITLE);
        metadata.put("sourceVersion", SOURCE_VERSION);
        metadata.put("section", chunk.section());
        // Spring AI document metadata forbids null values; an absent page represents a nullable page.
        metadata.put("chunkIndex", chunk.chunkIndex());
        metadata.put("contentHash", chunk.contentHash());
        metadata.put("visibility", "PUBLIC");
        metadata.put("indexVersion", INDEX_VERSION);
        metadata.put("category", chunk.category());
        return Collections.unmodifiableMap(metadata);
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Synthetic resume review guide is unavailable.");
    }

    record GuideChunk(String id, String category, String section, int chunkIndex, String content, String contentHash) {
    }
}
