package com.hermanli.careerpilot.identity;

import java.time.Instant;

public record CurrentUser(
        long id,
        String email,
        String status,
        Instant createdAt,
        CareerProfile profile
) {
    public record CareerProfile(
            String targetRole,
            String targetLocation,
            String workAuthorization,
            Integer weeklyHours,
            String educationSummary
    ) {
    }
}
