package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.ai.AiUnavailableException;
import com.hermanli.careerpilot.analysis.AnalysisReportService;
import com.hermanli.careerpilot.analysis.AnalysisReportView;
import com.hermanli.careerpilot.analysis.AnalysisStatus;
import com.hermanli.careerpilot.analysis.MatchReport;
import com.hermanli.careerpilot.analysis.InvalidAnalysisInputException;
import com.hermanli.careerpilot.api.CareerPilotExceptionHandler;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.config.AuthenticationWebConfig;
import com.hermanli.careerpilot.identity.AuthenticationInterceptor;
import com.hermanli.careerpilot.identity.CurrentUserIdArgumentResolver;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
@Import({
        CareerPilotExceptionHandler.class,
        AuthenticationWebConfig.class,
        AuthenticationInterceptor.class,
        CurrentUserIdArgumentResolver.class
})
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisReportService analysisReportService;

    @MockitoBean
    private SessionTokenService sessionTokenService;

    @Test
    void createsPendingAnalysisAndReturnsAcceptedContract() throws Exception {
        when(sessionTokenService.decodeUserId("valid-session")).thenReturn(OptionalLong.of(7L));
        when(analysisReportService.createPending(7L, 11L, 13L)).thenReturn(41L);
        doNothing().when(analysisReportService).runPending(7L, 41L);

        mockMvc.perform(post("/analyses")
                        .cookie(new jakarta.servlet.http.Cookie(SessionCookieService.COOKIE_NAME, "valid-session"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":11,\"jobDescriptionId\":13}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.analysisId").value(41))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.eventsUrl").value("/api/analyses/41/events"));
    }

    @Test
    void rejectsUnparsedOrUnownedInputsWithoutCreatingAnAnalysis() throws Exception {
        when(sessionTokenService.decodeUserId("valid-session")).thenReturn(OptionalLong.of(7L));
        when(analysisReportService.createPending(7L, 11L, 13L)).thenThrow(new InvalidAnalysisInputException());

        mockMvc.perform(post("/analyses")
                        .cookie(new jakarta.servlet.http.Cookie(SessionCookieService.COOKIE_NAME, "valid-session"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":11,\"jobDescriptionId\":13}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_RESOURCE_STATE"));
    }

    @Test
    void returnsSafeAiUnavailableContract() throws Exception {
        when(sessionTokenService.decodeUserId("valid-session")).thenReturn(OptionalLong.of(7L));
        when(analysisReportService.createPending(7L, 11L, 13L)).thenThrow(new AiUnavailableException());

        String response = mockMvc.perform(post("/analyses")
                        .cookie(new jakarta.servlet.http.Cookie(SessionCookieService.COOKIE_NAME, "valid-session"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":11,\"jobDescriptionId\":13}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value("AI features are not enabled for this environment."))
                .andReturn().getResponse().getContentAsString();

        assertTrue(!response.contains("DASHSCOPE_API_KEY") && !response.contains("provider"));
    }

    @Test
    void listsAndReadsOnlyTheAuthenticatedUsersReports() throws Exception {
        when(sessionTokenService.decodeUserId("valid-session")).thenReturn(OptionalLong.of(7L));
        AnalysisReportView report = new AnalysisReportView(
                41L, 11L, 13L, AnalysisStatus.COMPLETED, null, null, null, null,
                Instant.parse("2026-08-31T20:00:00Z"),
                Instant.parse("2026-08-31T20:00:01Z"),
                Instant.parse("2026-08-31T20:00:02Z")
        );
        when(analysisReportService.list(7L)).thenReturn(List.of(report));
        when(analysisReportService.get(7L, 41L)).thenReturn(report);

        mockMvc.perform(get("/analyses")
                        .cookie(new jakarta.servlet.http.Cookie(SessionCookieService.COOKIE_NAME, "valid-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].analysisId").value(41))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));
        mockMvc.perform(get("/analyses/41")
                        .cookie(new jakarta.servlet.http.Cookie(SessionCookieService.COOKIE_NAME, "valid-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(41));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/analyses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void hidesAnotherUsersAnalysisWithNotFound() throws Exception {
        when(sessionTokenService.decodeUserId("valid-session")).thenReturn(OptionalLong.of(7L));
        when(analysisReportService.get(7L, 99L)).thenThrow(new ResourceNotFoundException());

        mockMvc.perform(get("/analyses/99")
                        .cookie(new jakarta.servlet.http.Cookie(SessionCookieService.COOKIE_NAME, "valid-session")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void replaysCompletedReportsAsProgressReportAndOneDone() throws Exception {
        when(sessionTokenService.decodeUserId("valid-session")).thenReturn(OptionalLong.of(7L));
        MatchReport report = new MatchReport(50, List.of("Java"), List.of(), List.of("Docker"),
                List.of("Java project"), List.of("Docker risk"), List.of("Add Docker evidence"));
        when(analysisReportService.get(7L, 41L)).thenReturn(new AnalysisReportView(
                41L, 11L, 13L, AnalysisStatus.COMPLETED, report, 61L, null, null,
                Instant.parse("2026-08-31T20:00:00Z"), null, Instant.parse("2026-08-31T20:00:02Z")
        ));

        var result = mockMvc.perform(get("/analyses/41/events")
                        .cookie(new jakarta.servlet.http.Cookie(SessionCookieService.COOKIE_NAME, "valid-session")))
                .andExpect(request().asyncStarted())
                .andReturn();
        var completed = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:progress")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:report")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("partialMatches")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:plan")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")))
                .andReturn();
        String stream = completed.getResponse().getContentAsString();
        assertTrue(stream.indexOf("event:progress") < stream.indexOf("event:report"));
        assertTrue(stream.indexOf("event:report") < stream.indexOf("event:plan"));
        assertTrue(stream.indexOf("event:plan") < stream.indexOf("event:done"));
        assertEquals(1, stream.split("event:done", -1).length - 1);
    }

}
