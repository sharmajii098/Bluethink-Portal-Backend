package EmployeeManagementSystem.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handle404(NoHandlerFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", 404);
        body.put("error", "Not Found");
        body.put("message", "The requested API resource was not found.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> missingRecord(java.util.NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", 404, "message", "The requested record was not found."));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> denied(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", 403, "message", "Access denied for this account."));
    }


    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalidInput(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "message", ex.getMessage() == null ? "Invalid request" : ex.getMessage()));
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(org.springframework.web.server.ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("status", ex.getStatusCode().value(), "message", ex.getReason() == null ? "Request cannot be completed" : ex.getReason()));
    }
    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class, org.springframework.dao.PessimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> conflict(RuntimeException ex) {
        return ResponseEntity.status(409).body(Map.of("status", 409, "message", "This record changed or is being processed. Refresh before retrying."));
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(org.springframework.dao.DataIntegrityViolationException ex) {
        return ResponseEntity.status(409).body(Map.of("status", 409, "message", "This conflicts with an existing record or required data. Refresh and check the form."));
    }
}
