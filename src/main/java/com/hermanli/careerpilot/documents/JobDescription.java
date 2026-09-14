package com.hermanli.careerpilot.documents;

import java.time.Instant;

public record JobDescription(
        long id,
        String title,
        String companyName,
        String roleTitle,
        String rawText,
        String parseStatus,
        String parseError,
        Instant createdAt,
        Instant updatedAt
) {
}
