package EmployeeManagementSystem.service;

/** Marker for an upstream AI model failure, separate from HRMS/database failures. */
public class AiModelUnavailableException extends RuntimeException {
    public AiModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
