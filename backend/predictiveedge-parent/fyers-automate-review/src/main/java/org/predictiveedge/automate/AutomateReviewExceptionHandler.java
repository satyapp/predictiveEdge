package org.predictiveedge.automate;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AutomateReviewController.class)
public class AutomateReviewExceptionHandler {
    @ExceptionHandler(AutomateReviewFailure.class)
    ResponseEntity<Map<String, Object>> failure(AutomateReviewFailure failure) {
        HttpStatus status = switch (failure.code()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case INVALID_STATE -> HttpStatus.CONFLICT;
            case DELIVERY_FAILED -> HttpStatus.BAD_GATEWAY;
            case AI_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return body(status, "AUTOMATE_" + failure.code().name(), failure.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> invalid(IllegalArgumentException failure) {
        return body(HttpStatus.BAD_REQUEST, "AUTOMATE_INVALID_REQUEST", failure.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException failure) {
        Throwable cause = failure.getMostSpecificCause();
        String message = cause instanceof IllegalArgumentException ? cause.getMessage() : "The request body is not valid";
        return body(HttpStatus.BAD_REQUEST, "AUTOMATE_INVALID_REQUEST", message);
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message == null ? code : message,
                "timestamp", Instant.now().toString()));
    }
}
