package com.hermanli.careerpilot.review;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewSuggestion(
        String category,
        String priority,
        String finding,
        String resumeEvidence,
        String recommendation,
        String sourceId,
        String sourceTitle,
        ReviewCitation citation
) {

    public ReviewSuggestion(
            String category,
            String priority,
            String finding,
            String resumeEvidence,
            String recommendation,
            String sourceId,
            String sourceTitle
    ) {
        this(category, priority, finding, resumeEvidence, recommendation, sourceId, sourceTitle, null);
    }
}
