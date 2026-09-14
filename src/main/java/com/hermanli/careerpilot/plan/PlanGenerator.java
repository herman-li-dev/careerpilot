package com.hermanli.careerpilot.plan;

import com.hermanli.careerpilot.analysis.MatchReport;

public interface PlanGenerator {

    String generate(MatchReport report, String jobDescriptionParsedJson, String priorTaskProgressJson);

    default String generate(MatchReport report, String jobDescriptionParsedJson, String priorTaskProgressJson,
                            String normalizedGapsJson) {
        return generate(report, jobDescriptionParsedJson, priorTaskProgressJson);
    }
}
