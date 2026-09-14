package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.ApiResponse;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.documents.Resume;
import com.hermanli.careerpilot.documents.DocumentParsingService;
import com.hermanli.careerpilot.documents.ResumeRepository;
import com.hermanli.careerpilot.documents.ResumeUploadService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/resumes")
public class ResumeController {

    private final ResumeRepository resumeRepository;
    private final DocumentParsingService documentParsingService;
    private final ResumeUploadService resumeUploadService;

    public ResumeController(
            ResumeRepository resumeRepository,
            DocumentParsingService documentParsingService,
            ResumeUploadService resumeUploadService
    ) {
        this.resumeRepository = resumeRepository;
        this.documentParsingService = documentParsingService;
        this.resumeUploadService = resumeUploadService;
    }

    @PostMapping("/{resumeId}/parse")
    public ApiResponse<Resume> parse(
            @PathVariable long resumeId,
            @CurrentUserId long userId
    ) {
        return ApiResponse.success(documentParsingService.parseResume(resumeId, userId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Resume>> create(
            @CurrentUserId long userId,
            @Valid @RequestBody CreateResumeRequest request
    ) {
        Resume resume = resumeRepository.create(userId, request.title(), request.rawText());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resume));
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Resume>> upload(
            @CurrentUserId long userId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title
    ) {
        Resume resume = resumeUploadService.createFromUpload(userId, title, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resume));
    }

    @GetMapping
    public ApiResponse<List<Resume>> list(@CurrentUserId long userId) {
        return ApiResponse.success(resumeRepository.findAllByUserId(userId));
    }

    @GetMapping("/{resumeId}")
    public ApiResponse<Resume> get(
            @PathVariable long resumeId,
            @CurrentUserId long userId
    ) {
        return ApiResponse.success(resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(ResourceNotFoundException::new));
    }

    @DeleteMapping("/{resumeId}")
    public ResponseEntity<Void> delete(
            @PathVariable long resumeId,
            @CurrentUserId long userId
    ) {
        if (!resumeRepository.deleteByIdAndUserId(resumeId, userId)) {
            throw new ResourceNotFoundException();
        }
        return ResponseEntity.noContent().build();
    }

    public record CreateResumeRequest(
            @NotBlank(message = "Title is required.")
            @Size(max = 160, message = "Title must not exceed 160 characters.")
            String title,

            @NotBlank(message = "Resume text is required.")
            String rawText
    ) {
    }
}
