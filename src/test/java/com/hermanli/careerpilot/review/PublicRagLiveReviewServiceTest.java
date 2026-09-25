package com.hermanli.careerpilot.review;

import com.hermanli.careerpilot.ai.AiAvailability;
import com.hermanli.careerpilot.ai.AiUnavailableException;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.publicrag.PublicRagGuardProperties;
import com.hermanli.careerpilot.publicrag.PublicRagGuardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublicRagLiveReviewServiceTest {

    private ResumeReviewService resumeReviewService;
    private ObjectProvider<PublicRagGuardService> guardProvider;
    private PublicRagGuardService guard;
    private PublicRagGuardProperties guardProperties;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        resumeReviewService = mock(ResumeReviewService.class);
        guardProvider = mock(ObjectProvider.class);
        guard = mock(PublicRagGuardService.class);
        guardProperties = new PublicRagGuardProperties();
        guardProperties.setMaxOutputTokens(321);
        when(guardProvider.getIfAvailable()).thenReturn(guard);
    }

    @Test
    void verifiesOwnershipBeforeQuotaOrProviderWork() {
        when(resumeReviewService.prepare(7L, 99L)).thenThrow(new ResourceNotFoundException());
        PublicRagLiveReviewService service = service(true, true);

        assertThatThrownBy(() -> service.review(7L, "subject-a", 99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(guard);
        verify(resumeReviewService, never()).reviewPrepared(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void acquiresGuardImmediatelyBeforeReviewAndAlwaysClosesPermit() {
        ResumeReviewService.PreparedReview prepared = mock(ResumeReviewService.PreparedReview.class);
        PublicRagGuardService.GuardPermit permit = mock(PublicRagGuardService.GuardPermit.class);
        when(prepared.guardInputParts()).thenReturn(List.of("SKILLS: Synthetic Java evidence"));
        when(resumeReviewService.prepare(7L, 31L)).thenReturn(prepared);
        when(guard.acquire("subject-a", prepared.guardInputParts(), 321)).thenReturn(permit);
        when(resumeReviewService.reviewPrepared(prepared)).thenThrow(new IllegalStateException("synthetic failure"));
        PublicRagLiveReviewService service = service(true, true);

        assertThatThrownBy(() -> service.review(7L, "subject-a", 31L))
                .isInstanceOf(IllegalStateException.class);

        InOrder order = inOrder(resumeReviewService, guard, permit);
        order.verify(resumeReviewService).prepare(7L, 31L);
        order.verify(guard).acquire("subject-a", prepared.guardInputParts(), 321);
        order.verify(resumeReviewService).reviewPrepared(prepared);
        order.verify(permit).close();
    }

    @Test
    void doesNotTurnGuardRejectionIntoAnUnguardedFallback() {
        ResumeReviewService.PreparedReview prepared = mock(ResumeReviewService.PreparedReview.class);
        when(prepared.guardInputParts()).thenReturn(List.of("PROJECTS: Synthetic project evidence"));
        when(resumeReviewService.prepare(7L, 31L)).thenReturn(prepared);
        when(guard.acquire("subject-a", prepared.guardInputParts(), 321))
                .thenThrow(new IllegalStateException("guard rejected"));
        PublicRagLiveReviewService service = service(true, true);

        assertThatThrownBy(() -> service.review(7L, "subject-a", 31L))
                .isInstanceOf(IllegalStateException.class);

        verify(resumeReviewService, never()).reviewPrepared(prepared);
    }

    @Test
    void disabledAiRagOrGuardReturnsExistingAiUnavailableBoundary() {
        assertThatThrownBy(() -> service(false, true).review(7L, "subject-a", 31L))
                .isInstanceOf(AiUnavailableException.class);
        assertThatThrownBy(() -> service(true, false).review(7L, "subject-a", 31L))
                .isInstanceOf(AiUnavailableException.class);
        when(guardProvider.getIfAvailable()).thenReturn(null);
        assertThatThrownBy(() -> service(true, true).review(7L, "subject-a", 31L))
                .isInstanceOf(AiUnavailableException.class);

        verify(resumeReviewService, never()).prepare(7L, 31L);
    }

    private PublicRagLiveReviewService service(boolean aiEnabled, boolean ragEnabled) {
        return new PublicRagLiveReviewService(
                new AiAvailability(aiEnabled),
                resumeReviewService,
                guardProvider,
                guardProperties,
                ragEnabled
        );
    }
}
