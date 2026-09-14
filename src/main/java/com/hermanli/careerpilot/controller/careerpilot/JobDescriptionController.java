package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.ApiResponse;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.documents.JobDescription;
import com.hermanli.careerpilot.documents.DocumentParsingService;
import com.hermanli.careerpilot.documents.JobDescriptionRepository;
import com.hermanli.careerpilot.identity.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/job-descriptions")
public class JobDescriptionController {

    private final JobDescriptionRepository jobDescriptionRepository;
    private final DocumentParsingService documentParsingService;

    public JobDescriptionController(
            JobDescriptionRepository jobDescriptionRepository,
            DocumentParsingService documentParsingService
    ) {
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.documentParsingService = documentParsingService;
    }

    @PostMapping("/{jobDescriptionId}/parse")
    public ApiResponse<JobDescription> parse(
            @PathVariable long jobDescriptionId,
            @CurrentUserId long userId
    ) {
        return ApiResponse.success(documentParsingService.parseJobDescription(jobDescriptionId, userId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JobDescription>> create(
            @CurrentUserId long userId,
            @Valid @RequestBody CreateJobDescriptionRequest request
    ) {
        JobDescription jobDescription = jobDescriptionRepository.create(
                userId,
                request.title(),
                request.rawText()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(jobDescription));
    }

    @GetMapping
    public ApiResponse<List<JobDescription>> list(@CurrentUserId long userId) {
        return ApiResponse.success(jobDescriptionRepository.findAllByUserId(userId));
    }

    @GetMapping("/{jobDescriptionId}")
    public ApiResponse<JobDescription> get(
            @PathVariable long jobDescriptionId,
            @CurrentUserId long userId
    ) {
        return ApiResponse.success(jobDescriptionRepository.findByIdAndUserId(jobDescriptionId, userId)
                .orElseThrow(ResourceNotFoundException::new));
    }

    @DeleteMapping("/{jobDescriptionId}")
    public ResponseEntity<Void> delete(
            @PathVariable long jobDescriptionId,
            @CurrentUserId long userId
    ) {
        if (!jobDescriptionRepository.deleteByIdAndUserId(jobDescriptionId, userId)) {
            throw new ResourceNotFoundException();
        }
        return ResponseEntity.noContent().build();
    }

    public record CreateJobDescriptionRequest(
            @NotBlank(message = "Title is required.")
            @Size(max = 160, message = "Title must not exceed 160 characters.")
            String title,

            @NotBlank(message = "Job description text is required.")
            String rawText
    ) {
    }
}
