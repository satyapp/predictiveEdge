package org.predictiveedge.marketintelligence.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.predictiveedge.broker.domain.BrokerFailure;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "org.predictiveedge.marketintelligence.api")
public class MarketIntelligenceExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(
            MethodArgumentNotValidException failure, HttpServletRequest request) {
        List<FieldError> fields = failure.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), "INVALID", error.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(error(
                "MARKET_INTELLIGENCE_VALIDATION_FAILED", "Check the highlighted fields.", request, fields));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException failure, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(
                "MARKET_INTELLIGENCE_INVALID_REQUEST", failure.getMessage(), request, List.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> malformedRequest(
            HttpMessageNotReadableException failure, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(
                "MARKET_INTELLIGENCE_INVALID_REQUEST", "Request JSON is malformed or incomplete.",
                request, List.of()));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ErrorResponse> invalidQueryParameter(Exception failure, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(
                "MARKET_INTELLIGENCE_INVALID_REQUEST", "A required query parameter is missing or invalid.",
                request, List.of()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> invalidState(IllegalStateException failure, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(
                "MARKET_INTELLIGENCE_INVALID_STATE", failure.getMessage(), request, List.of()));
    }

    @ExceptionHandler(BrokerFailure.class)
    ResponseEntity<ErrorResponse> brokerFailure(BrokerFailure failure, HttpServletRequest request) {
        HttpStatus status = failure.code() == BrokerFailure.Code.CONNECTION_UNAVAILABLE
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(error(
                "MARKET_INTELLIGENCE_BROKER_" + failure.code().name(), failure.getMessage(), request, List.of()));
    }

    private static ErrorResponse error(
            String code, String message, HttpServletRequest request, List<FieldError> fields) {
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
        return new ErrorResponse(code, message, correlationId, Instant.now(), fields);
    }

    record ErrorResponse(
            String code, String message, String correlationId, Instant timestamp, List<FieldError> fieldErrors) {}
    record FieldError(String field, String code, String message) {}
}
