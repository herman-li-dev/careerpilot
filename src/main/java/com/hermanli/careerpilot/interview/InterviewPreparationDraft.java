package com.hermanli.careerpilot.interview;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InterviewPreparationDraft(
        @NotNull @Size(min = 5, max = 8) List<@Valid InterviewQuestionDraft> questions
) {
}
