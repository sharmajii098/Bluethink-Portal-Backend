package EmployeeManagementSystem.service;
import java.time.LocalDate;
import java.time.LocalDateTime;
public final class UpgradeRulesSelfTest {
    private static int assertions;
    static void check(boolean result, String label) { assertions++; if (!result) throw new AssertionError(label); }
    public static void main(String[] args) {
        LocalDateTime start = LocalDateTime.of(2026,9,17,9,0);
        check(!WorkSessionRules.expired(start,start.plusHours(23)),"should stay open before 24 hours");
        check(WorkSessionRules.expired(start,start.plusHours(24)),"must expire at 24 hours");
        check(WorkSessionRules.creditedMinutes(start,start.plusHours(4),"SIGNED_OFF") == 240,"four hours credited");
        check(WorkSessionRules.creditedMinutes(start,start.plusHours(24),"AUTO_CLOSED_UNVERIFIED") == 0,"unsigned zero hours");
        check(WorkSessionRules.remainingMinutes(240)==300,"four hours leaves five hours");
        check(WorkSessionRules.remainingMinutes(600)==0,"no negative remaining");
        for (String r : new String[]{"MANAGER","HR"}) check(LeaveWorkflowRules.notifyRole("EMPLOYEE",r),"employee "+r);
        check(!LeaveWorkflowRules.notifyRole("EMPLOYEE","ADMIN"),"employee not admin");
        check(LeaveWorkflowRules.notifyRole("MANAGER","ADMIN"),"manager admin");
        check(!LeaveWorkflowRules.notifyRole("MANAGER","HR"),"manager not hr");
        check(!LeaveWorkflowRules.notifyRole("MANAGER","MANAGER"),"manager no manager");
        check(LeaveWorkflowRules.notifyRole("HR","ADMIN"),"hr admin");
        check(!LeaveWorkflowRules.notifyRole("HR","HR"),"hr not hr");
        LocalDate d=LocalDate.of(2026,9,18);
        check(!LeaveWorkflowRules.conflicts(d,d,"FIRST_HALF",d,d,"SECOND_HALF"),"opposite halves permitted");
        check(LeaveWorkflowRules.conflicts(d,d,"FIRST_HALF",d,d,"FIRST_HALF"),"duplicate half disallowed");
        check(LeaveWorkflowRules.conflicts(d,d,"FULL_DAY",d,d,"SECOND_HALF"),"full overlaps half");
        check(!LeaveWorkflowRules.conflicts(d,d,"FULL_DAY",d.plusDays(1),d.plusDays(1),"FULL_DAY"),"nonoverlap");
        check(LeaveWorkflowRules.isShortLeave("FIRST_HALF"),"first half is short leave without category");
        check(LeaveWorkflowRules.isShortLeave("SECOND_HALF"),"second half is short leave without category");
        check(!LeaveWorkflowRules.isShortLeave("FULL_DAY"),"regular full-day leave keeps category");
        // Same matrix governs both leave and timesheet approvals.
        check(WorkflowApprovalPolicy.canReview("ADMIN", "MANAGER", false), "admin reviews manager");
        check(WorkflowApprovalPolicy.canReview("ADMIN", "HR", false), "admin reviews HR");
        check(!WorkflowApprovalPolicy.canReview("ADMIN", "EMPLOYEE", false), "admin cannot approve employee");
        check(!WorkflowApprovalPolicy.canReview("ADMIN", "ADMIN", false), "admin cannot approve peer admin");
        check(WorkflowApprovalPolicy.canReview("HR", "EMPLOYEE", false), "HR reviews employee");
        check(!WorkflowApprovalPolicy.canReview("HR", "MANAGER", false), "HR cannot review manager");
        check(!WorkflowApprovalPolicy.canReview("HR", "HR", false), "HR cannot approve HR");
        check(WorkflowApprovalPolicy.canReview("MANAGER", "EMPLOYEE", true), "manager reviews direct employee");
        check(!WorkflowApprovalPolicy.canReview("MANAGER", "EMPLOYEE", false), "manager cannot review other team");
        check(!WorkflowApprovalPolicy.canReview("MANAGER", "HR", true), "manager cannot review HR");
        check(!WorkflowApprovalPolicy.canReview("EMPLOYEE", "MANAGER", true), "employee cannot review");
        check(!WorkflowApprovalPolicy.canReview("ADMIN", null, false), "missing target role fails closed");
        check(!WorkflowApprovalPolicy.canReview(null, "EMPLOYEE", true), "missing reviewer fails closed");
        check(WorkflowApprovalPolicy.adminCanViewTimesheet("MANAGER"), "admin views manager timesheet");
        check(WorkflowApprovalPolicy.adminCanViewTimesheet("HR"), "admin views HR timesheet");
        check(!WorkflowApprovalPolicy.adminCanViewTimesheet("EMPLOYEE"), "admin cannot view employee timesheet");
        check(WorkflowApprovalPolicy.canReview("ROLE_ADMIN", "ROLE_MANAGER", false), "legacy prefixed roles supported");
        check(!WorkflowApprovalPolicy.canReview("ROLE_ADMIN", "ROLE_EMPLOYEE", false), "legacy prefixed employee remains view-only");
        System.out.println("PASS: " + assertions + " leave/attendance/approval policy assertions");
    }
}
