package com.hermanli.careerpilot.plan;

import java.time.Instant;

public record CareerPlan(
        long id,
        long userId,
        long analysisReportId,
        String title,
        String summary,
        short durationDays,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
