package EmployeeManagementSystem.messaging.kafka;

import lombok.Data;

@Data
public class EmployeeEvent {

    private Long employeeId;
    private String fullName;
    private String email;

    public EmployeeEvent() {
    }

    public EmployeeEvent(Long employeeId, String fullName, String email) {
        this.employeeId = employeeId;
        this.fullName = fullName;
        this.email = email;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // Guard against accidental disclosure in future application logging.
    @Override
    public String toString() {
        return "EmployeeEvent{payload=REDACTED}";
    }
}
