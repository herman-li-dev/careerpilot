package com.hermanli.careerpilot.plan;

import java.time.Instant;
import java.time.LocalDate;

public record PlanTask(
        long id,
        long careerPlanId,
        String title,
        String description,
        String status,
        LocalDate dueDate,
        String priority,
        String sourceEvidence,
        Instant completedAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
