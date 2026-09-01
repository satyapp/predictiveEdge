package org.predictiveedge.decision.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "org.predictiveedge.decision.api")
public class SafetyAssessmentExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalidAssessment(IllegalArgumentException failure) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "INVALID_SAFETY_ASSESSMENT", "message", failure.getMessage()));
    }
}
