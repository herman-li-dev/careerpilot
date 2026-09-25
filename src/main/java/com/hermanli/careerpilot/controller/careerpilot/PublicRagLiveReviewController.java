package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.ApiResponse;
import com.hermanli.careerpilot.identity.CurrentUserId;
import com.hermanli.careerpilot.review.PublicRagLiveReviewService;
import com.hermanli.careerpilot.review.ResumeReview;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag/resume")
@ConditionalOnProperty(name = "careerpilot.auth.clerk-application-enabled", havingValue = "true")
public class PublicRagLiveReviewController {

    private final PublicRagLiveReviewService liveReviewService;

    public PublicRagLiveReviewController(PublicRagLiveReviewService liveReviewService) {
        this.liveReviewService = liveReviewService;
    }

    @PostMapping("/review")
    public ApiResponse<ResumeReview> review(
            @Valid @RequestBody LiveReviewRequest request,
            @CurrentUserId long userId,
            @RequestAttribute(name = "careerpilotClerkSubject") String clerkSubject
    ) {
        return ApiResponse.success(liveReviewService.review(userId, clerkSubject, request.resumeId()));
    }

    public record LiveReviewRequest(@Positive long resumeId) {
    }
}
