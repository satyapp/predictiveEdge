package org.predictiveedge.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.predictiveedge.identity.domain.IdentityFailure;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class IdentityExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException failure, HttpServletRequest request) {
        List<FieldError> fields = failure.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), "INVALID", error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(error("AUTH_VALIDATION_FAILED", "Check the highlighted fields.", request, fields));
    }

    @ExceptionHandler(IdentityFailure.class)
    ResponseEntity<ErrorResponse> identityFailure(IdentityFailure failure, HttpServletRequest request) {
        HttpStatus status = switch (failure.code()) {
            case INVALID_CREDENTIALS, ACCESS_TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case ACCOUNT_UNAVAILABLE -> HttpStatus.FORBIDDEN;
            case OTP_INVALID -> HttpStatus.BAD_REQUEST;
            case OTP_EXPIRED -> HttpStatus.GONE;
            case OTP_ATTEMPTS_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status).body(error("AUTH_" + failure.code().name(), failure.getMessage(), request, List.of()));
    }

    private static ErrorResponse error(String code, String message, HttpServletRequest request, List<FieldError> fields) {
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
        return new ErrorResponse(code, message, correlationId, Instant.now(), fields);
    }

    record ErrorResponse(String code, String message, String correlationId, Instant timestamp, List<FieldError> fieldErrors) {}
    record FieldError(String field, String code, String message) {}
}
