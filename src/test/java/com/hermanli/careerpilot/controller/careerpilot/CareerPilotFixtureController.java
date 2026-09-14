package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/careerpilot-test")
class CareerPilotFixtureController {

    @PostMapping("/echo")
    ApiResponse<Map<String, String>> echo(@Valid @RequestBody FixtureRequest request) {
        return ApiResponse.success(Map.of("rawText", request.rawText()));
    }

    @PostMapping("/failure")
    ApiResponse<Void> failure() {
        throw new IllegalStateException("provider-secret-detail");
    }

    record FixtureRequest(
            @NotBlank(message = "Resume text is required.") String rawText
    ) {
    }
}
