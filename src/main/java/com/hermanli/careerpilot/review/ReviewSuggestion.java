package com.hermanli.careerpilot.review;

public record ReviewSuggestion(
        String category,
        String priority,
        String finding,
        String resumeEvidence,
        String recommendation,
        String sourceId,
        String sourceTitle
) {
}
