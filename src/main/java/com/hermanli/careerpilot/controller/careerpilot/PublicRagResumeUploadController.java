package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.ApiResponse;
import com.hermanli.careerpilot.documents.ResumeUploadTextExtractor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rag/resume")
@ConditionalOnProperty(
        name = {"careerpilot.public-rag.auth.enabled", "careerpilot.public-rag.upload.enabled"},
        havingValue = "true"
)
public class PublicRagResumeUploadController {

    private final ResumeUploadTextExtractor resumeUploadTextExtractor;

    public PublicRagResumeUploadController(ResumeUploadTextExtractor resumeUploadTextExtractor) {
        this.resumeUploadTextExtractor = resumeUploadTextExtractor;
    }

    @PostMapping(value = "/validate", consumes = "multipart/form-data")
    public ApiResponse<PublicResumeValidation> validate(@RequestPart("file") MultipartFile file) {
        ResumeUploadTextExtractor.ExtractedResume extracted = resumeUploadTextExtractor.extractWithMetadata(file);
        return ApiResponse.success(new PublicResumeValidation(
                true,
                extracted.documentType(),
                extracted.text().length()
        ));
    }

    public record PublicResumeValidation(
            boolean accepted,
            String documentType,
            int extractedCharacterCount
    ) {
    }
}
