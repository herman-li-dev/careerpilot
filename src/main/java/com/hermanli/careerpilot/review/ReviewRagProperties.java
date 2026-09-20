package com.hermanli.careerpilot.review;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Settings for the explicitly opt-in, public synthetic review index. */
@Component
@ConfigurationProperties(prefix = "careerpilot.review.rag")
class ReviewRagProperties {

    private boolean enabled;
    private int topK = 8;
    private double similarityThreshold = 0.50;
    private int maxContextChars = 6_000;
    private List<String> allowedSourceIds = new ArrayList<>(List.of(SyntheticReviewGuide.SOURCE_ID));
    private String indexVersion = SyntheticReviewGuide.INDEX_VERSION;

    @PostConstruct
    void validate() {
        if (topK < 1 || topK > 12 || similarityThreshold < 0.0 || similarityThreshold > 1.0
                || maxContextChars < 1 || maxContextChars > 12_000 || allowedSourceIds.isEmpty()
                || allowedSourceIds.stream().anyMatch(value -> value == null || !value.matches("[a-z0-9-]{3,80}"))
                || indexVersion == null || !SyntheticReviewGuide.INDEX_VERSION.equals(indexVersion)) {
            throw new IllegalStateException("Invalid public resume-review RAG configuration.");
        }
    }

    boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }

    List<String> getAllowedSourceIds() {
        return List.copyOf(allowedSourceIds);
    }

    public void setAllowedSourceIds(List<String> allowedSourceIds) {
        this.allowedSourceIds = allowedSourceIds == null ? List.of() : new ArrayList<>(allowedSourceIds);
    }

    String getIndexVersion() {
        return indexVersion;
    }

    public void setIndexVersion(String indexVersion) {
        this.indexVersion = indexVersion;
    }
}
