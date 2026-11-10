package org.predictiveedge.chart.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "org.predictiveedge.chart.api")
public class ChartIntelligenceExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException failure) {
        List<Map<String, String>> fields = failure.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(Map.of(
                "code", "CHART_INTELLIGENCE_VALIDATION_FAILED",
                "message", "Check the highlighted fields.",
                "fieldErrors", fields));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> invalidRequest(IllegalArgumentException failure) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "CHART_INTELLIGENCE_INVALID_REQUEST", "message", failure.getMessage()));
    }
}
