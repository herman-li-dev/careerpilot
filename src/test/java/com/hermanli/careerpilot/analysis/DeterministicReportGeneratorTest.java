package com.hermanli.careerpilot.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeterministicReportGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeterministicReportGenerator generator = new DeterministicReportGenerator(objectMapper);

    @Test
    void generatesAFixedEvidenceOnlyReport() throws Exception {
        String generatedJson = generator.generate(
                "{\"skills\":[\"Java\",\"Spring Boot\"]}",
                "{\"requiredSkills\":[\"Java\",\"Docker\"],\"preferredSkills\":[\"Spring Boot\"]}"
        );

        MatchReport report = objectMapper.readValue(generatedJson, MatchReport.class);

        assertEquals(66, report.matchScore());
        assertEquals(java.util.List.of("Java", "Spring Boot"), report.matchedSkills());
        assertEquals(java.util.List.of(), report.partialMatches());
        assertEquals(java.util.List.of("Docker"), report.missingSkills());
        assertEquals(report.matchedSkills(), report.strengths());
        assertEquals(report.missingSkills(), report.risks());
        assertEquals(report.missingSkills(), report.recommendations());
    }
}
