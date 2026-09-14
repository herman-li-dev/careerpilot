package com.hermanli.careerpilot.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermanli.careerpilot.analysis.MatchReport;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component
@Profile("deterministic")
public class DeterministicPlanGenerator implements PlanGenerator {

    private final ObjectMapper objectMapper;

    public DeterministicPlanGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(MatchReport report, String jobDescriptionParsedJson, String priorTaskProgressJson) {
        String evidence = Stream.of(
                        report.missingSkills(), report.partialMatches(), report.recommendations(),
                        report.risks(), report.matchedSkills(), report.strengths()
                )
                .flatMap(List::stream)
                .filter(item -> PlanGenerationService.canonicalFocusArea(item) != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The deterministic plan needs one recognized report focus."));
        String focusArea = PlanGenerationService.canonicalFocusArea(evidence);
        PlanDraft plan = new PlanDraft(
                "14-Day Preparation Plan",
                "Focus on the most important evidence-based gaps in the match report.",
                List.of(new PlanTaskDraft(
                        "Verify a reported gap",
                        "Check the available project evidence before making any application claim.",
                        1,
                        "HIGH",
                        evidence,
                        focusArea,
                        "EVIDENCE_VERIFICATION",
                        "A written yes/no conclusion with the supporting evidence"
                ))
        );
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Deterministic plan output could not be serialized.", exception);
        }
    }
}
