package com.hermanli.careerpilot.documents;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeUploadService {

    private static final String DEFAULT_TITLE = "Uploaded resume";
    private static final int MAX_TITLE_LENGTH = 160;

    private final ResumeRepository resumeRepository;
    private final ResumeUploadTextExtractor resumeUploadTextExtractor;

    public ResumeUploadService(
            ResumeRepository resumeRepository,
            ResumeUploadTextExtractor resumeUploadTextExtractor
    ) {
        this.resumeRepository = resumeRepository;
        this.resumeUploadTextExtractor = resumeUploadTextExtractor;
    }

    public Resume createFromUpload(long userId, String suppliedTitle, MultipartFile file) {
        String title = normalizeTitle(suppliedTitle);
        String rawText = resumeUploadTextExtractor.extract(file);
        return resumeRepository.create(userId, title, rawText);
    }

    private String normalizeTitle(String suppliedTitle) {
        if (suppliedTitle == null || suppliedTitle.isBlank()) {
            return DEFAULT_TITLE;
        }
        String title = suppliedTitle.trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new ResumeUploadException("VALIDATION_ERROR", "Title must not exceed 160 characters.");
        }
        return title;
    }
}
