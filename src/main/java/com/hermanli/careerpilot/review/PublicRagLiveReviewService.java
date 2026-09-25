package com.hermanli.careerpilot.review;

import com.hermanli.careerpilot.ai.AiAvailability;
import com.hermanli.careerpilot.ai.AiUnavailableException;
import com.hermanli.careerpilot.publicrag.PublicRagGuardProperties;
import com.hermanli.careerpilot.publicrag.PublicRagGuardService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PublicRagLiveReviewService {

    private final AiAvailability aiAvailability;
    private final ResumeReviewService resumeReviewService;
    private final ObjectProvider<PublicRagGuardService> guardProvider;
    private final PublicRagGuardProperties guardProperties;
    private final boolean ragEnabled;

    public PublicRagLiveReviewService(
            AiAvailability aiAvailability,
            ResumeReviewService resumeReviewService,
            ObjectProvider<PublicRagGuardService> guardProvider,
            PublicRagGuardProperties guardProperties,
            @Value("${careerpilot.review.rag.enabled:false}") boolean ragEnabled
    ) {
        this.aiAvailability = aiAvailability;
        this.resumeReviewService = resumeReviewService;
        this.guardProvider = guardProvider;
        this.guardProperties = guardProperties;
        this.ragEnabled = ragEnabled;
    }

    public ResumeReview review(long userId, String clerkSubject, long resumeId) {
        aiAvailability.requireEnabled();
        if (!ragEnabled) {
            throw new AiUnavailableException();
        }
        PublicRagGuardService guard = guardProvider.getIfAvailable();
        if (guard == null) {
            throw new AiUnavailableException();
        }

        ResumeReviewService.PreparedReview prepared = resumeReviewService.prepare(userId, resumeId);
        try (PublicRagGuardService.GuardPermit ignored = guard.acquire(
                clerkSubject,
                prepared.guardInputParts(),
                guardProperties.getMaxOutputTokens()
        )) {
            return resumeReviewService.reviewPrepared(prepared);
        }
    }
}
