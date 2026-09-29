package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.CareerPilotExceptionHandler;
import com.hermanli.careerpilot.documents.DocumentParsingService;
import com.hermanli.careerpilot.documents.JobDescription;
import com.hermanli.careerpilot.documents.JobDescriptionRepository;
import com.hermanli.careerpilot.identity.AuthenticationInterceptor;
import com.hermanli.careerpilot.identity.CurrentUserIdArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobDescriptionControllerTest {

    private DocumentParsingService documentParsingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        documentParsingService = mock(DocumentParsingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new JobDescriptionController(mock(JobDescriptionRepository.class), documentParsingService))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new CareerPilotExceptionHandler())
                .build();
    }

    @Test
    void passesTheVerifiedClerkSubjectToGuardedJobParsing() throws Exception {
        when(documentParsingService.parseJobDescription(13L, 7L, "subject-a")).thenReturn(parsedJobDescription());

        mockMvc.perform(post("/job-descriptions/13/parse")
                        .requestAttr(AuthenticationInterceptor.USER_ID_ATTRIBUTE, 7L)
                        .requestAttr("careerpilotClerkSubject", "subject-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.clerkSubject").doesNotExist());

        verify(documentParsingService).parseJobDescription(13L, 7L, "subject-a");
    }

    @Test
    void passesNoSubjectWhenTheRequestIsNotClerkAuthenticated() throws Exception {
        when(documentParsingService.parseJobDescription(13L, 7L, null)).thenReturn(parsedJobDescription());

        mockMvc.perform(post("/job-descriptions/13/parse")
                        .requestAttr(AuthenticationInterceptor.USER_ID_ATTRIBUTE, 7L))
                .andExpect(status().isOk());

        verify(documentParsingService).parseJobDescription(13L, 7L, null);
    }

    private JobDescription parsedJobDescription() {
        return new JobDescription(13L, "Synthetic job", null, null, "Synthetic text", "COMPLETED", null,
                Instant.EPOCH, Instant.EPOCH);
    }
}
