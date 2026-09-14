package com.hermanli.careerpilot.analysis;

import java.time.Instant;

public record AnalysisReportView(
        long analysisId,
        long resumeId,
        long jobDescriptionId,
        AnalysisStatus status,
        MatchReport report,
        Long planId,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
}
