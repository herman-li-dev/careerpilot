package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.CareerPilotExceptionHandler;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.config.AuthenticationWebConfig;
import com.hermanli.careerpilot.identity.AuthenticationInterceptor;
import com.hermanli.careerpilot.identity.CurrentUserIdArgumentResolver;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import com.hermanli.careerpilot.plan.CareerPlan;
import com.hermanli.careerpilot.plan.PlanService;
import com.hermanli.careerpilot.plan.PlanTask;
import com.hermanli.careerpilot.plan.UpdatePlanTaskRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalLong;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlanController.class)
@Import({
        CareerPilotExceptionHandler.class,
        AuthenticationWebConfig.class,
        AuthenticationInterceptor.class,
        CurrentUserIdArgumentResolver.class
})
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlanService planService;

    @MockitoBean
    private SessionTokenService sessionTokenService;

    @Test
    void returnsOwnedPlanAndTasks() throws Exception {
        authenticate();
        when(planService.get(7L, 12L)).thenReturn(plan());
        when(planService.listTasks(7L, 12L)).thenReturn(List.of(task("TODO", null)));

        mockMvc.perform(get("/plans/12").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.durationDays").value(14));
        mockMvc.perform(get("/plans/12/tasks").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(3))
                .andExpect(jsonPath("$.data[0].dueDate").value("2026-09-04"));
    }

    @Test
    void updatesOnlyStatusAndDueDate() throws Exception {
        authenticate();
        PlanTask updated = task("COMPLETED", Instant.parse("2026-09-01T12:00:00Z"));
        when(planService.updateTask(eq(7L), eq(12L), eq(3L),
                eq(new UpdatePlanTaskRequest("COMPLETED", LocalDate.of(2026, 9, 12))))).thenReturn(updated);

        mockMvc.perform(patch("/plans/12/tasks/3")
                        .cookie(sessionCookie())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"dueDate\":\"2026-09-12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").value("2026-09-01T12:00:00Z"));
    }

    @Test
    void rejectsInvalidOrEmptyTaskUpdates() throws Exception {
        authenticate();
        mockMvc.perform(patch("/plans/12/tasks/3").cookie(sessionCookie())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LATER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(patch("/plans/12/tasks/3").cookie(sessionCookie())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors.updateProvided").value("Provide a status or due date."));
    }

    @Test
    void hidesAnotherUsersPlanOrTask() throws Exception {
        authenticate();
        when(planService.get(7L, 12L)).thenThrow(new ResourceNotFoundException());
        when(planService.updateTask(eq(7L), eq(12L), eq(3L), org.mockito.ArgumentMatchers.any(UpdatePlanTaskRequest.class)))
                .thenThrow(new ResourceNotFoundException());

        mockMvc.perform(get("/plans/12").cookie(sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(patch("/plans/12/tasks/3").cookie(sessionCookie())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TODO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void requiresAuthenticationForPlans() throws Exception {
        mockMvc.perform(get("/plans/12"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void regeneratesOnlyTheOwnedPlansRemainingTasks() throws Exception {
        authenticate();
        when(planService.regenerateRemaining(7L, 12L)).thenReturn(List.of(
                task("COMPLETED", Instant.parse("2026-09-01T12:00:00Z")),
                new PlanTask(4L, 12L, "New task", "New description", "TODO", LocalDate.of(2026, 9, 5),
                        "HIGH", "Docker", null, null, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"))
        ));

        mockMvc.perform(post("/plans/12/regenerate-remaining").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].title").value("New task"));
    }

    private void authenticate() {
        when(sessionTokenService.decodeUserId("valid-session")).thenReturn(OptionalLong.of(7L));
    }

    private jakarta.servlet.http.Cookie sessionCookie() {
        return new jakarta.servlet.http.Cookie(SessionCookieService.COOKIE_NAME, "valid-session");
    }

    private CareerPlan plan() {
        return new CareerPlan(12L, 7L, 41L, "14-day plan", "Focused preparation", (short) 14, "ACTIVE",
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
    }

    private PlanTask task(String status, Instant completedAt) {
        return new PlanTask(3L, 12L, "Practice Java", "Complete an exercise", status,
                LocalDate.of(2026, 9, 4), "HIGH", "Programming", completedAt,
                null,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
    }
}
