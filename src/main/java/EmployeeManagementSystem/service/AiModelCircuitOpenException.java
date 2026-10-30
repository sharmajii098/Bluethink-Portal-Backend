package EmployeeManagementSystem.service;

/** A local model is in recovery after repeated failures; not a portal-database error. */
public final class AiModelCircuitOpenException extends RuntimeException {
    private final int retryAfterSeconds;

    public AiModelCircuitOpenException(int retryAfterSeconds) {
        super("The AI model is temporarily recovering after repeated failures.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() { return retryAfterSeconds; }
}
