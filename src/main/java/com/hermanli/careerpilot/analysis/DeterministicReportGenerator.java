package com.hermanli.careerpilot.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Profile("deterministic")
public class DeterministicReportGenerator implements ReportGenerator {

    private final ObjectMapper objectMapper;

    public DeterministicReportGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(String resumeParsedJson, String jobDescriptionParsedJson) {
        try {
            Map<String, String> resumeSkills = values(objectMapper.readTree(resumeParsedJson), "skills");
            Map<String, String> jobSkills = values(objectMapper.readTree(jobDescriptionParsedJson), "requiredSkills");
            values(objectMapper.readTree(jobDescriptionParsedJson), "preferredSkills")
                    .forEach(jobSkills::putIfAbsent);

            List<String> matchedSkills = jobSkills.entrySet().stream()
                    .filter(entry -> resumeSkills.containsKey(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();
            List<String> missingSkills = jobSkills.entrySet().stream()
                    .filter(entry -> !resumeSkills.containsKey(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();
            int matchScore = jobSkills.isEmpty() ? 100 : matchedSkills.size() * 100 / jobSkills.size();
            return objectMapper.writeValueAsString(new MatchReport(
                    matchScore,
                    matchedSkills,
                    List.of(),
                    missingSkills,
                    matchedSkills,
                    missingSkills,
                    missingSkills
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Validated document JSON could not be read.", exception);
        }
    }

    private Map<String, String> values(JsonNode root, String field) {
        Map<String, String> values = new LinkedHashMap<>();
        JsonNode array = root.path(field);
        if (!array.isArray()) {
            return values;
        }
        for (JsonNode value : array) {
            if (value.isTextual() && !value.asText().isBlank()) {
                String text = value.asText().trim();
                values.putIfAbsent(text.toLowerCase(Locale.ROOT), text);
            }
        }
        return values;
    }
}
