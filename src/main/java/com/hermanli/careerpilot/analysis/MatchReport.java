package com.hermanli.careerpilot.analysis;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MatchReport(
        @NotNull @Min(0) @Max(100) Integer matchScore,
        @NotNull List<@NotBlank String> matchedSkills,
        @NotNull List<@NotBlank String> partialMatches,
        @NotNull List<@NotBlank String> missingSkills,
        @NotNull List<@NotBlank String> strengths,
        @NotNull List<@NotBlank String> risks,
        @NotNull List<@NotBlank String> recommendations
) {
}
