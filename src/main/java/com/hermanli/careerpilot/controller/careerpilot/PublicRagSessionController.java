package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag")
@ConditionalOnProperty(name = "careerpilot.public-rag.auth.enabled", havingValue = "true")
public class PublicRagSessionController {

    @GetMapping("/session")
    public ApiResponse<PublicRagSessionStatus> session() {
        return ApiResponse.success(new PublicRagSessionStatus(true));
    }

    public record PublicRagSessionStatus(boolean authenticated) {
    }
}
