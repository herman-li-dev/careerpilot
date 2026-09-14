package com.hermanli.careerpilot.documents;

import java.time.Instant;

public record Resume(
        long id,
        String title,
        String rawText,
        String parseStatus,
        String parseError,
        Instant createdAt,
        Instant updatedAt
) {
}
