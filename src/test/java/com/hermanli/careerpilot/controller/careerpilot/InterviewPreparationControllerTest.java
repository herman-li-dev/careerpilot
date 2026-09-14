package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.CareerPilotExceptionHandler;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.config.AuthenticationWebConfig;
import com.hermanli.careerpilot.identity.AuthenticationInterceptor;
import com.hermanli.careerpilot.identity.CurrentUserIdArgumentResolver;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import com.hermanli.careerpilot.interview.InterviewModelUnavailableException;
import com.hermanli.careerpilot.interview.InterviewPreparationService;
import com.hermanli.careerpilot.interview.InterviewQuestion;
import com.hermanli.careerpilot.interview.InterviewQuestionType;
import com.hermanli.careerpilot.interview.InterviewSessionView;
import com.hermanli.careerpilot.interview.InvalidInterviewPreparationStateException;
import com.hermanli.careerpilot.interview.InvalidInterviewQuestionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewPreparationController.class)
@Import({CareerPilotExceptionHandler.class, AuthenticationWebConfig.class, AuthenticationInterceptor.class,
        CurrentUserIdArgumentResolver.class})
class InterviewPreparationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewPreparationService service;

    @MockitoBean
    private SessionTokenService sessionTokenService;

    @Test
    void createsThenReturnsTheOwnedInterviewSession() throws Exception {
        authenticate();
        when(service.create(7L, 41L)).thenReturn(new InterviewPreparationService.CreationResult(session(), true));
        when(service.get(7L, 88L)).thenReturn(session());

        mockMvc.perform(post("/analyses/41/interview-prep").cookie(cookie()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(88))
                .andExpect(jsonPath("$.data.questions[0].questionType").value("TECHNICAL_GAP"));
        mockMvc.perform(get("/interview-sessions/88").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisReportId").value(41));
    }

    @Test
    void returnsOkWhenTheAnalysisAlreadyHasASession() throws Exception {
        authenticate();
        when(service.create(7L, 41L)).thenReturn(new InterviewPreparationService.CreationResult(session(), false));

        mockMvc.perform(post("/analyses/41/interview-prep").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(88));
    }

    @Test
    void returnsSafeErrorsAndHidesOtherUsersResources() throws Exception {
        authenticate();
        when(service.get(7L, 99L)).thenThrow(new ResourceNotFoundException());
        when(service.create(7L, 42L)).thenThrow(new InvalidInterviewPreparationStateException());
        when(service.create(7L, 43L)).thenThrow(new InvalidInterviewQuestionException());
        when(service.create(7L, 44L)).thenThrow(new InterviewModelUnavailableException());

        mockMvc.perform(get("/interview-sessions/99").cookie(cookie()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/analyses/42/interview-prep").cookie(cookie()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_RESOURCE_STATE"));
        mockMvc.perform(post("/analyses/43/interview-prep").cookie(cookie()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("MODEL_OUTPUT_INVALID"));
        String unavailable = mockMvc.perform(post("/analyses/44/interview-prep").cookie(cookie()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("MODEL_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(unavailable.contains("provider detail"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/analyses/41/interview-prep"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/interview-sessions/88"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    private void authenticate() {
        when(sessionTokenService.decodeUserId("valid-session")).thenReturn(OptionalLong.of(7L));
    }

    private jakarta.servlet.http.Cookie cookie() {
        return new jakarta.servlet.http.Cookie(SessionCookieService.COOKIE_NAME, "valid-session");
    }

    private InterviewSessionView session() {
        return new InterviewSessionView(88L, 41L, "Interview Preparation", Instant.EPOCH, List.of(
                new InterviewQuestion(1L, (short) 1, InterviewQuestionType.TECHNICAL_GAP, "Synthetic question",
                        "Synthetic goal", "Docker", "Synthetic tip", Instant.EPOCH)
        ));
    }
}
