package org.predictiveedge.guardian.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.predictiveedge.guardian.application.TradeGuardianFailure;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(basePackages = "org.predictiveedge.guardian.api")
public class TradeGuardianExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(
            MethodArgumentNotValidException failure, HttpServletRequest request) {
        List<FieldError> fields = failure.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), "INVALID", error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(error(
                "GUARDIAN_VALIDATION_FAILED", "Check the highlighted fields.", request, fields));
    }

    @ExceptionHandler(TradeGuardianFailure.class)
    ResponseEntity<ErrorResponse> guardianFailure(
            TradeGuardianFailure failure, HttpServletRequest request) {
        HttpStatus status = switch (failure.code()) {
            case MONITORING_CASE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RECOMMENDATION_ALREADY_MONITORED, CONCURRENT_MODIFICATION -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(error(
                "GUARDIAN_" + failure.code().name(), failure.getMessage(), request, List.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidRequest(
            IllegalArgumentException failure, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(
                "GUARDIAN_INVALID_REQUEST", failure.getMessage(), request, List.of()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> invalidTransition(
            IllegalStateException failure, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(
                "GUARDIAN_INVALID_TRANSITION", failure.getMessage(), request, List.of()));
    }

    private static ErrorResponse error(
            String code, String message, HttpServletRequest request, List<FieldError> fields) {
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return new ErrorResponse(code, message, correlationId, Instant.now(), fields);
    }

    record ErrorResponse(
            String code, String message, String correlationId, Instant timestamp, List<FieldError> fieldErrors) {}

    record FieldError(String field, String code, String message) {}
}
