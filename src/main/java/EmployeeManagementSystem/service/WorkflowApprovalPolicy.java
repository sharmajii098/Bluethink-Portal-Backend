package EmployeeManagementSystem.service;

import java.util.Locale;

/** Role policy shared by leave and timesheet approval. Fail closed on missing roles. */
public final class WorkflowApprovalPolicy {
    private WorkflowApprovalPolicy() { }

    public static boolean canReview(String reviewerRole, String requesterRole, boolean directReport) {
        String reviewer = normalize(reviewerRole);
        String requester = normalize(requesterRole);
        return switch (reviewer) {
            case "ADMIN" -> "MANAGER".equals(requester) || "HR".equals(requester);
            case "HR" -> "EMPLOYEE".equals(requester);
            case "MANAGER" -> "EMPLOYEE".equals(requester) && directReport;
            default -> false;
        };
    }

    private static String normalize(String role) {
        if (role == null) return "";
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }

    public static boolean adminCanViewTimesheet(String requesterRole) {
        return canReview("ADMIN", requesterRole, false);
    }
}
