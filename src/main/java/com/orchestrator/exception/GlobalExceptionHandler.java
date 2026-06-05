package com.orchestrator.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FlowNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(FlowNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), null);
    }

    @ExceptionHandler(InvalidFlowDefinitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalid(InvalidFlowDefinitionException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }

    @ExceptionHandler(ContractValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ContractValidationException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), e.getErrors());
    }

    @ExceptionHandler(IntegrationExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleIntegration(IntegrationExecutionException e) {
        log.error("Erro de integração", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        log.error("Erro inesperado", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro inesperado: " + e.getMessage(), null);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, Object details) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (details != null) body.put("details", details);
        return ResponseEntity.status(status).body(body);
    }
}
