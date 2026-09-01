package org.predictiveedge.broker.api;

import java.util.Map;

import org.predictiveedge.broker.connection.BrokerConnectionFailure;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "org.predictiveedge.broker.api")
public class BrokerConnectionExceptionHandler {
    @ExceptionHandler(BrokerConnectionFailure.class)
    ResponseEntity<Map<String, String>> brokerFailure(BrokerConnectionFailure failure) {
        HttpStatus status = switch (failure.code()) {
            case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            case NOT_CONNECTED -> HttpStatus.CONFLICT;
            case ALREADY_CONNECTED -> HttpStatus.CONFLICT;
            case INVALID_STATE -> HttpStatus.BAD_REQUEST;
            case CONNECTION_FAILED -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(Map.of("code", failure.code().name(), "message", failure.getMessage()));
    }
}
