package com.hermanli.careerpilot.documents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermanli.careerpilot.ai.AiAvailability;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Set;

@Service
public class DocumentParsingService {

    private static final String INVALID_OUTPUT_MESSAGE =
            "The parser returned invalid structured data. Please try again.";
    private static final String UNAVAILABLE_MESSAGE =
            "The document could not be parsed. Please try again.";
    private static final Set<String> RESUME_FIELDS = Set.of(
            "skills", "education", "projects", "workExperience", "certifications"
    );
    private static final Set<String> JOB_DESCRIPTION_FIELDS = Set.of(
            "companyName", "roleTitle", "location", "responsibilities",
            "requiredSkills", "preferredSkills", "experienceRequirements"
    );

    private final ResumeRepository resumeRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final DocumentParser documentParser;
    private final ObjectMapper objectMapper;
    private final AiAvailability aiAvailability;

    public DocumentParsingService(
            ResumeRepository resumeRepository,
            JobDescriptionRepository jobDescriptionRepository,
            DocumentParser documentParser,
            ObjectMapper objectMapper,
            AiAvailability aiAvailability
    ) {
        this.resumeRepository = resumeRepository;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.documentParser = documentParser;
        this.objectMapper = objectMapper;
        this.aiAvailability = aiAvailability;
    }

    public Resume parseResume(long resumeId, long userId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(ResourceNotFoundException::new);
        aiAvailability.requireEnabled();
        if (!resumeRepository.markRunning(resumeId, userId)) {
            throw new InvalidDocumentStateException();
        }

        String parsedJson;
        try {
            parsedJson = validateResume(documentParser.parseResume(resume.rawText()));
        } catch (InvalidParsedDocumentException exception) {
            resumeRepository.markFailed(resumeId, userId, INVALID_OUTPUT_MESSAGE);
            return findResume(resumeId, userId);
        } catch (RuntimeException exception) {
            resumeRepository.markFailed(resumeId, userId, UNAVAILABLE_MESSAGE);
            return findResume(resumeId, userId);
        }
        resumeRepository.markCompleted(resumeId, userId, parsedJson);
        return findResume(resumeId, userId);
    }

    public JobDescription parseJobDescription(long jobDescriptionId, long userId) {
        JobDescription jobDescription = jobDescriptionRepository.findByIdAndUserId(jobDescriptionId, userId)
                .orElseThrow(ResourceNotFoundException::new);
        aiAvailability.requireEnabled();
        if (!jobDescriptionRepository.markRunning(jobDescriptionId, userId)) {
            throw new InvalidDocumentStateException();
        }

        String parsedJson;
        try {
            parsedJson = validateJobDescription(documentParser.parseJobDescription(jobDescription.rawText()));
        } catch (InvalidParsedDocumentException exception) {
            jobDescriptionRepository.markFailed(jobDescriptionId, userId, INVALID_OUTPUT_MESSAGE);
            return findJobDescription(jobDescriptionId, userId);
        } catch (RuntimeException exception) {
            jobDescriptionRepository.markFailed(jobDescriptionId, userId, UNAVAILABLE_MESSAGE);
            return findJobDescription(jobDescriptionId, userId);
        }
        jobDescriptionRepository.markCompleted(jobDescriptionId, userId, parsedJson);
        return findJobDescription(jobDescriptionId, userId);
    }

    private Resume findResume(long resumeId, long userId) {
        return resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private JobDescription findJobDescription(long jobDescriptionId, long userId) {
        return jobDescriptionRepository.findByIdAndUserId(jobDescriptionId, userId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private String validateResume(String rawModelOutput) {
        JsonNode root = readObject(rawModelOutput, RESUME_FIELDS);
        RESUME_FIELDS.forEach(field -> requireStringArray(root, field));
        return writeJson(root);
    }

    private String validateJobDescription(String rawModelOutput) {
        JsonNode root = readObject(rawModelOutput, JOB_DESCRIPTION_FIELDS);
        for (String field : Set.of("companyName", "roleTitle", "location")) {
            JsonNode value = root.get(field);
            if (!value.isNull() && (!value.isTextual() || value.asText().isBlank())) {
                throw new InvalidParsedDocumentException();
            }
        }
        for (String field : Set.of(
                "responsibilities", "requiredSkills", "preferredSkills", "experienceRequirements"
        )) {
            requireStringArray(root, field);
        }
        return writeJson(root);
    }

    private JsonNode readObject(String rawModelOutput, Set<String> expectedFields) {
        try {
            JsonNode root = objectMapper.readTree(rawModelOutput);
            if (root == null || !root.isObject() || root.size() != expectedFields.size()) {
                throw new InvalidParsedDocumentException();
            }
            Iterator<String> fields = root.fieldNames();
            while (fields.hasNext()) {
                if (!expectedFields.contains(fields.next())) {
                    throw new InvalidParsedDocumentException();
                }
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new InvalidParsedDocumentException();
        }
    }

    private void requireStringArray(JsonNode root, String field) {
        JsonNode values = root.get(field);
        if (values == null || !values.isArray()) {
            throw new InvalidParsedDocumentException();
        }
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new InvalidParsedDocumentException();
            }
        }
    }

    private String writeJson(JsonNode root) {
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new InvalidParsedDocumentException();
        }
    }

    private static class InvalidParsedDocumentException extends RuntimeException {
    }
}
