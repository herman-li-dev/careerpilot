package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.ApiResponse;
import com.hermanli.careerpilot.identity.CurrentUserId;
import com.hermanli.careerpilot.interview.InterviewPreparationService;
import com.hermanli.careerpilot.interview.InterviewSessionView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterviewPreparationController {

    private final InterviewPreparationService interviewPreparationService;

    public InterviewPreparationController(InterviewPreparationService interviewPreparationService) {
        this.interviewPreparationService = interviewPreparationService;
    }

    @PostMapping("/analyses/{analysisId}/interview-prep")
    public ResponseEntity<ApiResponse<InterviewSessionView>> create(
            @PathVariable long analysisId, @CurrentUserId long userId
    ) {
        InterviewPreparationService.CreationResult result = interviewPreparationService.create(userId, analysisId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(result.session()));
    }

    @GetMapping("/interview-sessions/{sessionId}")
    public ApiResponse<InterviewSessionView> get(@PathVariable long sessionId, @CurrentUserId long userId) {
        return ApiResponse.success(interviewPreparationService.get(userId, sessionId));
    }
}
