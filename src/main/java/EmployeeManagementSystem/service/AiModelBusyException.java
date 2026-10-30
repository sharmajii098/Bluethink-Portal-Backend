package EmployeeManagementSystem.service;

/** A local AI provider call was rejected before invocation because inference slots are full. */
public class AiModelBusyException extends RuntimeException {
    public AiModelBusyException() {
        super("All configured AI model inference slots are occupied.");
    }
}
