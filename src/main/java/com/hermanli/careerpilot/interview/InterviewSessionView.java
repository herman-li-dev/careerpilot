package com.hermanli.careerpilot.interview;

import java.time.Instant;
import java.util.List;

public record InterviewSessionView(
        long id,
        long analysisReportId,
        String title,
        Instant createdAt,
        List<InterviewQuestion> questions
) {
}
