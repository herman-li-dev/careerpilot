package com.hermanli.careerpilot.interview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GeneratedInterviewQuestion(
        @NotNull InterviewQuestionType questionType,
        @NotBlank @Size(max = 1000) String questionText,
        @NotBlank @Size(max = 1000) String assessmentGoal,
        @NotBlank @Size(max = 20) String sourceEvidenceId,
        @NotBlank @Size(max = 1000) String preparationTip
) {
}
