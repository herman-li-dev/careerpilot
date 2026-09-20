package com.hermanli.careerpilot.review;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retrieves only exact chunks from the fixed public guide. Vector-store contents are treated as
 * untrusted until their metadata and content hash match the bundled guide.
 */
class SyntheticVectorRag {

    private static final Logger log = LoggerFactory.getLogger(SyntheticVectorRag.class);
    private final VectorStore vectorStore;
    private final SyntheticReviewGuide guide;
    private final Map<String, SyntheticReviewGuide.GuideChunk> chunksById;
    private final ReviewRagProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final int maxContextChars;
    private volatile boolean ready;

    SyntheticVectorRag(
            VectorStore vectorStore,
            SyntheticReviewGuide guide,
            ReviewRagProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this(vectorStore, guide, properties, new TransactionTemplate(transactionManager), false);
    }

    SyntheticVectorRag(VectorStore vectorStore, SyntheticReviewGuide guide, int maxContextChars) {
        this(vectorStore, guide, propertiesWithMaxContext(maxContextChars), null, true);
    }

    SyntheticVectorRag(VectorStore vectorStore, SyntheticReviewGuide guide) {
        this(vectorStore, guide, new ReviewRagProperties(), null, true);
    }

    private SyntheticVectorRag(
            VectorStore vectorStore,
            SyntheticReviewGuide guide,
            ReviewRagProperties properties,
            TransactionTemplate transactionTemplate,
            boolean ready
    ) {
        this.vectorStore = vectorStore;
        this.guide = guide;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        properties.validate();
        this.maxContextChars = properties.getMaxContextChars();
        this.ready = ready;
        this.chunksById = guide.chunks().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                SyntheticReviewGuide.GuideChunk::id,
                chunk -> chunk
        ));
    }

    @EventListener(ApplicationReadyEvent.class)
    void indexBundledGuide() {
        ready = false;
        if (transactionTemplate == null) {
            log.warn("Public synthetic review knowledge index is unavailable; lexical fallback remains active.");
            return;
        }
        try {
            List<Document> documents = guide.chunks().stream()
                    .map(chunk -> new Document(chunk.id(), chunk.content(), SyntheticReviewGuide.metadata(chunk)))
                    .toList();
            transactionTemplate.executeWithoutResult(status -> {
                vectorStore.delete(indexFilterExpression());
                vectorStore.add(documents);
            });
            ready = true;
            log.info("Public synthetic review knowledge index is ready: chunks={}", documents.size());
        } catch (RuntimeException exception) {
            // Do not include exception messages: providers and drivers can include sensitive query data.
            log.warn("Public synthetic review knowledge index is unavailable; lexical fallback remains active.");
        }
    }

    List<RetrievedGuidance> retrieve(String query) {
        if (!ready || query == null || query.isBlank()) {
            if (!ready) {
                log.warn("Synthetic vector retrieval is not ready; lexical fallback remains active.");
            }
            return List.of();
        }
        try {
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query.length() <= maxContextChars ? query : query.substring(0, maxContextChars))
                    .topK(properties.getTopK())
                    .similarityThreshold(properties.getSimilarityThreshold())
                    .filterExpression(filterExpression())
                    .build());
            return validateAndBound(documents == null ? List.of() : documents);
        } catch (RuntimeException exception) {
            // Do not include query, document text, or provider/driver exception detail in logs.
            log.warn("Synthetic vector retrieval is unavailable; lexical fallback remains active.");
            throw new VectorRetrievalUnavailable();
        }
    }

    private List<RetrievedGuidance> validateAndBound(List<Document> documents) {
        List<RetrievedGuidance> accepted = new ArrayList<>();
        Set<String> contentHashes = new HashSet<>();
        int contextChars = 0;
        for (Document document : documents.stream().limit(properties.getTopK()).toList()) {
            SyntheticReviewGuide.GuideChunk expected = chunksById.get(document.getId());
            if (expected == null || !matchesBundledChunk(document, expected) || !contentHashes.add(expected.contentHash())) {
                continue;
            }
            if (contextChars + expected.content().length() > maxContextChars) {
                break;
            }
            contextChars += expected.content().length();
            accepted.add(new RetrievedGuidance(expected, excerpt(expected.content())));
        }
        accepted.sort(Comparator.comparingInt(guidance -> guidance.chunk().chunkIndex()));
        return List.copyOf(accepted);
    }

    private boolean matchesBundledChunk(Document document, SyntheticReviewGuide.GuideChunk expected) {
        if (!expected.content().equals(document.getText())) {
            return false;
        }
        Map<String, Object> metadata = document.getMetadata();
        return SyntheticReviewGuide.SOURCE_ID.equals(metadata.get("sourceId"))
                && SyntheticReviewGuide.SOURCE_TITLE.equals(metadata.get("sourceTitle"))
                && SyntheticReviewGuide.SOURCE_VERSION.equals(metadata.get("sourceVersion"))
                && expected.section().equals(metadata.get("section"))
                && pageIsNull(metadata)
                && chunkIndexMatches(metadata.get("chunkIndex"), expected.chunkIndex())
                && expected.contentHash().equals(metadata.get("contentHash"))
                && "PUBLIC".equals(metadata.get("visibility"))
                && properties.getIndexVersion().equals(metadata.get("indexVersion"))
                && expected.category().equals(metadata.get("category"));
    }

    private boolean pageIsNull(Map<String, Object> metadata) {
        return !metadata.containsKey("page") || metadata.get("page") == null;
    }

    private boolean chunkIndexMatches(Object value, int expected) {
        return value instanceof Number number && number.intValue() == expected;
    }

    private String filterExpression() {
        String sourceFilter = properties.getAllowedSourceIds().stream()
                .map(sourceId -> "sourceId == '" + sourceId + "'")
                .collect(java.util.stream.Collectors.joining(" || ", "(", ")"));
        return sourceFilter
                + " && sourceVersion == '" + SyntheticReviewGuide.SOURCE_VERSION + "'"
                + " && visibility == 'PUBLIC' && indexVersion == '" + properties.getIndexVersion() + "'";
    }

    private String indexFilterExpression() {
        return "sourceId == '" + SyntheticReviewGuide.SOURCE_ID + "'";
    }

    private static ReviewRagProperties propertiesWithMaxContext(int maxContextChars) {
        ReviewRagProperties properties = new ReviewRagProperties();
        properties.setMaxContextChars(maxContextChars);
        return properties;
    }

    int maxContextChars() {
        return maxContextChars;
    }

    private String excerpt(String content) {
        return content.substring(0, Math.min(240, content.length()));
    }

    record RetrievedGuidance(SyntheticReviewGuide.GuideChunk chunk, String excerpt) {
        ReviewCitation citation() {
            return new ReviewCitation(
                    SyntheticReviewGuide.SOURCE_ID,
                    SyntheticReviewGuide.SOURCE_TITLE,
                    SyntheticReviewGuide.SOURCE_VERSION,
                    chunk.section(),
                    null,
                    chunk.chunkIndex(),
                    excerpt
            );
        }
    }

    static class VectorRetrievalUnavailable extends RuntimeException {
    }
}
