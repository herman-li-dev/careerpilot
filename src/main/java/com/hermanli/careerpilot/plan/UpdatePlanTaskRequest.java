package com.hermanli.careerpilot.plan;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record UpdatePlanTaskRequest(
        @Pattern(regexp = "TODO|IN_PROGRESS|COMPLETED|SKIPPED", message = "Status must be TODO, IN_PROGRESS, COMPLETED, or SKIPPED.")
        String status,
        LocalDate dueDate
) {
    @AssertTrue(message = "Provide a status or due date.")
    public boolean isUpdateProvided() {
        return status != null || dueDate != null;
    }
}
