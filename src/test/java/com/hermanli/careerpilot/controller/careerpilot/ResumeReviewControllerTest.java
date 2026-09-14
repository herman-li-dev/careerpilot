package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.CareerPilotExceptionHandler;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.config.AuthenticationWebConfig;
import com.hermanli.careerpilot.documents.InvalidDocumentStateException;
import com.hermanli.careerpilot.identity.AuthenticationInterceptor;
import com.hermanli.careerpilot.identity.CurrentUserIdArgumentResolver;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import com.hermanli.careerpilot.review.ResumeReview;
import com.hermanli.careerpilot.review.ResumeReviewService;
import com.hermanli.careerpilot.review.ReviewSuggestion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.OptionalLong;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumeReviewController.class)
@Import({
        CareerPilotExceptionHandler.class,
        AuthenticationWebConfig.class,
        AuthenticationInterceptor.class,
        CurrentUserIdArgumentResolver.class
})
class ResumeReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResumeReviewService resumeReviewService;

    @MockitoBean
    private SessionTokenService sessionTokenService;

    @Test
    void returnsTheExistingEnvelopeForAnOwnedCompletedResume() throws Exception {
        authenticate();
        when(resumeReviewService.review(7L, 31L)).thenReturn(new ResumeReview(
                31L,
                "MODEL_ASSISTED_SYNTHETIC_LEXICAL_RULES",
                "synthetic-review-rules-v1",
                List.of(new ReviewSuggestion(
                        "SKILLS", "HIGH", "Existing evidence may be clearer.", "Java",
                        "If accurate, clarify existing context.", "careerpilot-synthetic-resume-review-v1",
                        "CareerPilot synthetic resume review guide"
                ))
        ));

        mockMvc.perform(post("/resumes/{resumeId}/review", 31L).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resumeId").value(31))
                .andExpect(jsonPath("$.data.reviewType").value("MODEL_ASSISTED_SYNTHETIC_LEXICAL_RULES"))
                .andExpect(jsonPath("$.data.suggestions[0].resumeEvidence").value("Java"))
                .andExpect(jsonPath("$.data.suggestions[0].sourceId")
                        .value("careerpilot-synthetic-resume-review-v1"));
    }

    @Test
    void mapsMissingOrCrossOwnerAndUnparsedResumesToExistingSafeErrors() throws Exception {
        authenticate();
        when(resumeReviewService.review(7L, 99L)).thenThrow(new ResourceNotFoundException());
        when(resumeReviewService.review(7L, 32L)).thenThrow(new InvalidDocumentStateException());

        mockMvc.perform(post("/resumes/{resumeId}/review", 99L).cookie(sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/resumes/{resumeId}/review", 32L).cookie(sessionCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_RESOURCE_STATE"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/resumes/{resumeId}/review", 31L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    private void authenticate() {
        when(sessionTokenService.decodeUserId("valid-session")).thenReturn(OptionalLong.of(7L));
    }

    private jakarta.servlet.http.Cookie sessionCookie() {
        return new jakarta.servlet.http.Cookie(SessionCookieService.COOKIE_NAME, "valid-session");
    }
}
