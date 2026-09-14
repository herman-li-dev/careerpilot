package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.analysis.AnalysisReportService;
import com.hermanli.careerpilot.analysis.AnalysisReportView;
import com.hermanli.careerpilot.api.ApiResponse;
import com.hermanli.careerpilot.identity.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/analyses")
public class AnalysisController {

    private final AnalysisReportService analysisReportService;

    public AnalysisController(AnalysisReportService analysisReportService) {
        this.analysisReportService = analysisReportService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateAnalysisResponse>> create(
            @CurrentUserId long userId,
            @Valid @RequestBody CreateAnalysisRequest request
    ) {
        long analysisId = analysisReportService.createPending(userId, request.resumeId(), request.jobDescriptionId());
        CompletableFuture.runAsync(() -> analysisReportService.runPending(userId, analysisId));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
                new CreateAnalysisResponse(analysisId, "PENDING", "/api/analyses/" + analysisId + "/events")
        ));
    }

    @GetMapping
    public ApiResponse<List<AnalysisReportView>> list(@CurrentUserId long userId) {
        return ApiResponse.success(analysisReportService.list(userId));
    }

    @GetMapping("/{analysisId}")
    public ApiResponse<AnalysisReportView> get(
            @PathVariable long analysisId,
            @CurrentUserId long userId
    ) {
        return ApiResponse.success(analysisReportService.get(userId, analysisId));
    }

    @GetMapping(path = "/{analysisId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @PathVariable long analysisId,
            @CurrentUserId long userId
    ) throws java.io.IOException {
        AnalysisReportView analysis = analysisReportService.get(userId, analysisId);
        SseEmitter emitter = new SseEmitter(0L);
        if (analysis.status() == com.hermanli.careerpilot.analysis.AnalysisStatus.COMPLETED) {
            emitter.send(SseEmitter.event().name("progress").data(new ProgressEvent(
                    analysis.analysisId(), "COMPLETED", "Match report is ready."
            )));
            emitter.send(SseEmitter.event().name("report").data(new ReportEvent(
                    analysis.analysisId(),
                    analysis.report().matchScore(),
                    analysis.report().matchedSkills(),
                    analysis.report().partialMatches(),
                    analysis.report().missingSkills(),
                    analysis.report().strengths(),
                    analysis.report().risks(),
                    analysis.report().recommendations()
            )));
            if (analysis.planId() != null) {
                emitter.send(SseEmitter.event().name("plan").data(new PlanEvent(
                        analysis.analysisId(), analysis.planId()
                )));
            }
            emitter.send(SseEmitter.event().name("done").data(new DoneEvent(analysis.analysisId(), "COMPLETED")));
        } else if (analysis.status() == com.hermanli.careerpilot.analysis.AnalysisStatus.FAILED) {
            emitter.send(SseEmitter.event().name("error").data(new ErrorEvent(
                    analysis.analysisId(), analysis.errorCode(), analysis.errorMessage()
            )));
        } else {
            emitter.send(SseEmitter.event().name("progress").data(new ProgressEvent(
                    analysis.analysisId(), analysis.status().name(), "Analysis is in progress."
            )));
        }
        emitter.complete();
        return emitter;
    }

    public record CreateAnalysisRequest(
            @Positive(message = "Resume ID must be positive.") long resumeId,
            @Positive(message = "Job description ID must be positive.") long jobDescriptionId
    ) {
    }

    public record CreateAnalysisResponse(long analysisId, String status, String eventsUrl) {
    }

    public record ProgressEvent(long analysisId, String stage, String message) {
    }

    public record ReportEvent(
            long analysisId,
            int matchScore,
            java.util.List<String> matchedSkills,
            java.util.List<String> partialMatches,
            java.util.List<String> missingSkills,
            java.util.List<String> strengths,
            java.util.List<String> risks,
            java.util.List<String> recommendations
    ) {
    }

    public record DoneEvent(long analysisId, String status) {
    }

    public record PlanEvent(long analysisId, Long planId) {
    }

    public record ErrorEvent(long analysisId, String code, String message) {
    }
}
