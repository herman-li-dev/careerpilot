package com.hermanli.careerpilot.api;

import com.hermanli.careerpilot.ai.AiUnavailableException;
import com.hermanli.careerpilot.identity.DuplicateEmailException;
import com.hermanli.careerpilot.identity.AuthenticationRequiredException;
import com.hermanli.careerpilot.identity.InvalidCredentialsException;
import com.hermanli.careerpilot.documents.InvalidDocumentStateException;
import com.hermanli.careerpilot.analysis.InvalidAnalysisInputException;
import com.hermanli.careerpilot.plan.InvalidPlanStateException;
import com.hermanli.careerpilot.plan.PlanGenerationService;
import com.hermanli.careerpilot.documents.ResumeUploadException;
import com.hermanli.careerpilot.interview.InterviewModelUnavailableException;
import com.hermanli.careerpilot.interview.InvalidInterviewPreparationStateException;
import com.hermanli.careerpilot.interview.InvalidInterviewQuestionException;
import com.hermanli.careerpilot.publicrag.PublicRagGuardRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.hermanli.careerpilot.controller.careerpilot")
public class CareerPilotExceptionHandler {

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationRequired() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(new ApiError(
                "AUTHENTICATION_REQUIRED",
                "Authentication is required.",
                Map.of()
        )));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(new ApiError(
                "INVALID_CREDENTIALS",
                "Email or password is incorrect.",
                Map.of()
        )));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(new ApiError(
                "EMAIL_ALREADY_REGISTERED",
                "An account already exists for this email.",
                Map.of()
        )));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(new ApiError(
                "RESOURCE_NOT_FOUND",
                "The requested resource was not found.",
                Map.of()
        )));
    }

    @ExceptionHandler(InvalidDocumentStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidDocumentState() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(new ApiError(
                "INVALID_RESOURCE_STATE",
                "The resource cannot be parsed in its current state.",
                Map.of()
        )));
    }

    @ExceptionHandler(InvalidAnalysisInputException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidAnalysisInput() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(new ApiError(
                "INVALID_RESOURCE_STATE",
                "The selected resume and job description must be parsed before analysis.",
                Map.of()
        )));
    }

    @ExceptionHandler(InvalidPlanStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPlanState(InvalidPlanStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(new ApiError(
                "INVALID_RESOURCE_STATE",
                exception.getMessage(),
                Map.of()
        )));
    }

    @ExceptionHandler(PlanGenerationService.InvalidPlanException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPlanGeneration() {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(new ApiError(
                "MODEL_OUTPUT_INVALID",
                "The remaining plan could not be generated. Please try again.",
                Map.of()
        )));
    }

    @ExceptionHandler(InvalidInterviewPreparationStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidInterviewPreparationState() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(new ApiError(
                "INVALID_RESOURCE_STATE",
                "Interview preparation requires a completed analysis with parsed inputs.",
                Map.of()
        )));
    }

    @ExceptionHandler(InvalidInterviewQuestionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidInterviewQuestions() {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(new ApiError(
                "MODEL_OUTPUT_INVALID",
                "Interview questions could not be generated. Please try again.",
                Map.of()
        )));
    }

    @ExceptionHandler(InterviewModelUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInterviewModelUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(new ApiError(
                "MODEL_UNAVAILABLE",
                "Interview questions are temporarily unavailable. Please try again.",
                Map.of()
        )));
    }

    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(new ApiError(
                "AI_UNAVAILABLE",
                "AI features are not enabled for this environment.",
                Map.of()
        )));
    }

    @ExceptionHandler(PublicRagGuardRejectedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePublicRagGuardRejected(
            PublicRagGuardRejectedException exception
    ) {
        HttpStatus status = exception.reason() == PublicRagGuardRejectedException.Reason.TOKEN_LIMIT
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.TOO_MANY_REQUESTS;
        String code = switch (exception.reason()) {
            case TOKEN_LIMIT -> "PUBLIC_RAG_TOKEN_LIMIT";
            case CONCURRENCY_LIMIT -> "PUBLIC_RAG_BUSY";
            case USER_DAILY_LIMIT -> "PUBLIC_RAG_USER_LIMIT";
            case GLOBAL_DAILY_LIMIT -> "PUBLIC_RAG_GLOBAL_LIMIT";
        };
        return ResponseEntity.status(status).body(ApiResponse.error(new ApiError(
                code,
                exception.getMessage(),
                Map.of()
        )));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().stream()
                .sorted((left, right) -> left.getField().compareTo(right.getField()))
                .forEach(fieldError -> fieldErrors.putIfAbsent(
                        fieldError.getField(),
                        validationMessage(fieldError)
                ));

        return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError(
                "VALIDATION_ERROR",
                "The request contains invalid fields.",
                fieldErrors
        )));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedBody() {
        return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError(
                "VALIDATION_ERROR",
                "The request body is malformed.",
                Map.of()
        )));
    }

    @ExceptionHandler(ResumeUploadException.class)
    public ResponseEntity<ApiResponse<Void>> handleResumeUpload(ResumeUploadException exception) {
        HttpStatus status = "FILE_TOO_LARGE".equals(exception.code())
                ? HttpStatus.PAYLOAD_TOO_LARGE
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.error(new ApiError(
                exception.code(),
                exception.getMessage(),
                Map.of()
        )));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.error(new ApiError(
                "FILE_TOO_LARGE",
                "The uploaded resume file exceeds the 5 MiB limit.",
                Map.of()
        )));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingUploadFile() {
        return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError(
                "VALIDATION_ERROR",
                "A resume file is required.",
                Map.of()
        )));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedMultipart() {
        return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError(
                "VALIDATION_ERROR",
                "The upload could not be read.",
                Map.of()
        )));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(new ApiError(
                "INTERNAL_ERROR",
                "An unexpected error occurred.",
                Map.of()
        )));
    }

    private String validationMessage(FieldError fieldError) {
        String message = fieldError.getDefaultMessage();
        return message == null || message.isBlank() ? "This field is invalid." : message;
    }
}
