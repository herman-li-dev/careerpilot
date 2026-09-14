package com.hermanli.careerpilot.identity;

import java.time.Instant;

public record RegisteredUser(
        long id,
        String email,
        String status,
        Instant createdAt
) {
}
