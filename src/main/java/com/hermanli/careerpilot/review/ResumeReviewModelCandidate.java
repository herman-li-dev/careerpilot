package com.hermanli.careerpilot.review;

public record ResumeReviewModelCandidate(
        String ruleId,
        String evidenceId,
        String category,
        String priority,
        String ruleRecommendation,
        String resumeEvidence
) {
}
