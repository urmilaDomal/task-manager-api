package com.taskmanager.task_manager_api.exception;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


     // 404 - Task not found
    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(TaskNotFoundException ex){
        
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
            }
        
            private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
                // TODO Auto-generated method stub

                Map<String, Object> map = new HashMap<>();
                map.put("timestamp", LocalTime.now().toString());
                map.put("status", status.value());
                map.put("error", status.getReasonPhrase());
                map.put("message", message);
                
                return ResponseEntity.status(status).body(map);
            }

            @ExceptionHandler(TaskAccessDeniedException.class)
            public ResponseEntity<Map<String, Object>> handleAccessDenied(TaskAccessDeniedException ex) {
                // Deliberately returns 404 (not 403) — see TaskAccessDeniedException javadoc.
                // Returning 403 would confirm the resource exists; 404 gives nothing away.
                return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
            }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return buildError(HttpStatus.BAD_REQUEST, message);
    }

    // 500 - Catch-all for unexpected errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("An unexpected error occurred"));
    }
 
    private Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", message);
        return body;
    }

}
