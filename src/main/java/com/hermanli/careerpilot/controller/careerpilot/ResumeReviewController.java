package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.ApiResponse;
import com.hermanli.careerpilot.identity.CurrentUserId;
import com.hermanli.careerpilot.review.ResumeReview;
import com.hermanli.careerpilot.review.ResumeReviewService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/resumes")
public class ResumeReviewController {

    private final ResumeReviewService resumeReviewService;

    public ResumeReviewController(ResumeReviewService resumeReviewService) {
        this.resumeReviewService = resumeReviewService;
    }

    @PostMapping("/{resumeId}/review")
    public ApiResponse<ResumeReview> review(@PathVariable long resumeId, @CurrentUserId long userId) {
        return ApiResponse.success(resumeReviewService.review(userId, resumeId));
    }
}
