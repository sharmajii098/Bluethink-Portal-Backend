package EmployeeManagementSystem.service;
import java.time.LocalDate;
/** Pure policy helpers for leave recipients and date/half-day collision. */
public final class LeaveWorkflowRules {
    private LeaveWorkflowRules() { }
    public static boolean notifyRole(String actor, String recipient) {
        return switch (actor == null ? "" : actor.toUpperCase()) {
            case "EMPLOYEE" -> "HR".equals(recipient) || "MANAGER".equals(recipient);
            case "MANAGER" -> "ADMIN".equals(recipient);
            case "HR" -> "ADMIN".equals(recipient);
            default -> false;
        };
    }
    /** Short Leave is determined by half-day duration, not a leave category. */
    public static boolean isShortLeave(String duration) {
        return "FIRST_HALF".equalsIgnoreCase(duration) || "SECOND_HALF".equalsIgnoreCase(duration);
    }
    public static boolean conflicts(LocalDate start, LocalDate end, String duration,
                                    LocalDate oldStart, LocalDate oldEnd, String oldDuration) {
        if (start == null || end == null || oldStart == null || oldEnd == null) return false;
        if (end.isBefore(oldStart) || oldEnd.isBefore(start)) return false;
        if (!start.equals(end) || !oldStart.equals(oldEnd)) return true;
        if (duration == null || "FULL_DAY".equalsIgnoreCase(duration)
                || oldDuration == null || "FULL_DAY".equalsIgnoreCase(oldDuration)) return true;
        return duration.equalsIgnoreCase(oldDuration);
    }
}
