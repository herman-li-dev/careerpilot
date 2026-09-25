package com.hermanli.careerpilot.publicrag;

import com.hermanli.careerpilot.api.CareerPilotExceptionHandler;
import com.hermanli.careerpilot.controller.careerpilot.PublicRagLiveReviewController;
import com.hermanli.careerpilot.identity.AuthenticationInterceptor;
import com.hermanli.careerpilot.identity.CurrentUserIdArgumentResolver;
import com.hermanli.careerpilot.review.PublicRagLiveReviewService;
import com.hermanli.careerpilot.review.ResumeReview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicRagLiveReviewControllerTest {

    private PublicRagLiveReviewService liveReviewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        liveReviewService = mock(PublicRagLiveReviewService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicRagLiveReviewController(liveReviewService))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new CareerPilotExceptionHandler())
                .build();
    }

    @Test
    void derivesIdentityFromRequestAttributesAndReturnsTheExistingReviewContract() throws Exception {
        when(liveReviewService.review(7L, "subject-a", 31L)).thenReturn(new ResumeReview(
                31L,
                "MODEL_ASSISTED_SYNTHETIC_VECTOR_RAG",
                "synthetic-review-guide-v1",
                List.of()
        ));

        mockMvc.perform(post("/rag/resume/review")
                        .requestAttr(AuthenticationInterceptor.USER_ID_ATTRIBUTE, 7L)
                        .requestAttr("careerpilotClerkSubject", "subject-a")
                        .contentType("application/json")
                        .content("{\"resumeId\":31}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumeId").value(31))
                .andExpect(jsonPath("$.data.reviewType").value("MODEL_ASSISTED_SYNTHETIC_VECTOR_RAG"))
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.clerkSubject").doesNotExist());
        verify(liveReviewService).review(7L, "subject-a", 31L);
    }

    @Test
    void rejectsInvalidRequestBeforeCallingTheLiveService() throws Exception {
        mockMvc.perform(post("/rag/resume/review")
                        .requestAttr(AuthenticationInterceptor.USER_ID_ATTRIBUTE, 7L)
                        .requestAttr("careerpilotClerkSubject", "subject-a")
                        .contentType("application/json")
                        .content("{\"resumeId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void mapsGuardDenialsWithoutRunningAFallback() throws Exception {
        assertGuardError(PublicRagGuardRejectedException.Reason.TOKEN_LIMIT, 400, "PUBLIC_RAG_TOKEN_LIMIT");
        assertGuardError(PublicRagGuardRejectedException.Reason.CONCURRENCY_LIMIT, 429, "PUBLIC_RAG_BUSY");
        assertGuardError(PublicRagGuardRejectedException.Reason.USER_DAILY_LIMIT, 429, "PUBLIC_RAG_USER_LIMIT");
        assertGuardError(PublicRagGuardRejectedException.Reason.GLOBAL_DAILY_LIMIT, 429, "PUBLIC_RAG_GLOBAL_LIMIT");
    }

    private void assertGuardError(
            PublicRagGuardRejectedException.Reason reason,
            int status,
            String code
    ) throws Exception {
        doThrow(new PublicRagGuardRejectedException(reason))
                .when(liveReviewService).review(7L, "subject-a", 31L);
        mockMvc.perform(post("/rag/resume/review")
                        .requestAttr(AuthenticationInterceptor.USER_ID_ATTRIBUTE, 7L)
                        .requestAttr("careerpilotClerkSubject", "subject-a")
                        .contentType("application/json")
                        .content("{\"resumeId\":31}"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.error.code").value(code));
    }
}
