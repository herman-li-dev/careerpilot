package com.hermanli.careerpilot.interview;

import java.time.Instant;

public record InterviewQuestion(
        long id,
        short questionOrder,
        InterviewQuestionType questionType,
        String questionText,
        String assessmentGoal,
        String sourceEvidence,
        String preparationTip,
        Instant createdAt
) {
}
