package com.hermanli.careerpilot.plan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PlanDraft(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String summary,
        @NotEmpty @Size(max = 8) List<@Valid PlanTaskDraft> tasks
) {
}
