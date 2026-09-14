package com.hermanli.careerpilot.plan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlanTaskDraft(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @Min(1) @Max(14) int dayOffset,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH") String priority,
        @NotBlank String sourceEvidence,
        @NotBlank @Pattern(regexp = "PROGRAMMING|TESTING|TROUBLESHOOTING|PROBLEM_SOLVING|SDLC|INTEGRATION|REQUIREMENTS_ANALYSIS|COMMUNICATION|COLLABORATION|LEARNING_AGILITY|CLOUD_COMPUTING|MICROSERVICES|TDD|DEVOPS_DELIVERY") String focusArea,
        @NotBlank @Pattern(regexp = "RESUME_APPLICATION|INTERVIEW_STORY|EVIDENCE_VERIFICATION|CONCEPT_LEARNING") String taskType,
        @NotBlank String deliverable
) {

    public String persistedDescription() {
        return description + "\n\nDeliverable: " + deliverable;
    }
}
