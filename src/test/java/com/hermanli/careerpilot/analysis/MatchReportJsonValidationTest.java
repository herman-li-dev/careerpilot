package com.hermanli.careerpilot.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchReportJsonValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsTheCompleteFixedReportJson() {
        MatchReport report = assertDoesNotThrow(() -> objectMapper.readValue(validJson(), MatchReport.class));

        assertFalse(validator.validate(report).iterator().hasNext());
    }

    @ParameterizedTest
    @MethodSource("invalidFixedFieldJson")
    void rejectsInvalidValuesForEveryFixedField(String field, String json) throws Exception {
        MatchReport report = objectMapper.readValue(json, MatchReport.class);

        assertTrue(
                validator.validate(report).stream()
                        .anyMatch(violation -> violation.getPropertyPath().toString().startsWith(field)),
                () -> "Expected a validation error for " + field
        );
    }

    @Test
    void rejectsAnUnknownFieldSoTheJsonShapeRemainsFixed() {
        assertThrows(Exception.class, () -> objectMapper.readValue(
                validJson().replace("}", ",\"extra\":true}"),
                MatchReport.class
        ));
    }

    private static Stream<Arguments> invalidFixedFieldJson() {
        return Stream.of(
                Arguments.of("matchScore", validJson().replace("\"matchScore\": 72", "\"matchScore\": -1")),
                Arguments.of("matchScore", validJson().replace("\"matchScore\": 72", "\"matchScore\": 101")),
                Arguments.of("matchedSkills", validJson().replace("[\"Java\"]", "[\" \"]")),
                Arguments.of("partialMatches", validJson().replace("[\"Spring\"]", "[\" \"]")),
                Arguments.of("missingSkills", validJson().replace("[\"Docker\"]", "[\" \"]")),
                Arguments.of("strengths", validJson().replace("[\"Built a backend project\"]", "[\" \"]")),
                Arguments.of("risks", validJson().replace("[\"No deployment evidence\"]", "[\" \"]")),
                Arguments.of("recommendations", validJson().replace("[\"Add deployment evidence\"]", "[\" \"]"))
        );
    }

    private static String validJson() {
        return """
                {
                  "matchScore": 72,
                  "matchedSkills": ["Java"],
                  "partialMatches": ["Spring"],
                  "missingSkills": ["Docker"],
                  "strengths": ["Built a backend project"],
                  "risks": ["No deployment evidence"],
                  "recommendations": ["Add deployment evidence"]
                }
                """;
    }
}
