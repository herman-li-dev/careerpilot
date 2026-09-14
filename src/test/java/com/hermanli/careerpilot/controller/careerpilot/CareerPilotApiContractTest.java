package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.CareerPilotExceptionHandler;
import com.hermanli.careerpilot.identity.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CareerPilotFixtureController.class)
@Import(CareerPilotExceptionHandler.class)
class CareerPilotApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionTokenService sessionTokenService;

    @Test
    void wrapsSuccessfulResponse() throws Exception {
        mockMvc.perform(post("/careerpilot-test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawText\":\"Synthetic resume text\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rawText").value("Synthetic resume text"))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void returnsStableEnglishFieldErrors() throws Exception {
        mockMvc.perform(post("/careerpilot-test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawText\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("The request contains invalid fields."))
                .andExpect(jsonPath("$.error.fieldErrors.rawText").value("Resume text is required."));
    }

    @Test
    void hidesParserDetailsForMalformedJson() throws Exception {
        mockMvc.perform(post("/careerpilot-test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("The request body is malformed."))
                .andExpect(content().string(not(containsString("JsonParseException"))));
    }

    @Test
    void hidesInternalExceptionDetails() throws Exception {
        mockMvc.perform(post("/careerpilot-test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("provider-secret-detail"))));
    }

}
