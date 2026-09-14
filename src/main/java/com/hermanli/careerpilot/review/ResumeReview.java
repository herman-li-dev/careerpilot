package com.hermanli.careerpilot.review;

import java.util.List;

public record ResumeReview(
        long resumeId,
        String reviewType,
        String knowledgeBaseVersion,
        List<ReviewSuggestion> suggestions
) {
}
