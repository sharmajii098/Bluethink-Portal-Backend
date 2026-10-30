package EmployeeManagementSystem.service;

import EmployeeManagementSystem.controller.FrontendApiController;
import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.entity.RegisterEmployee;
import EmployeeManagementSystem.entity.SupportTicket;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.repository.AiAuditRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiActionServiceTest {
    EmployeeRepository employees = mock(EmployeeRepository.class);
    SupportTicketService tickets = mock(SupportTicketService.class);
    FrontendApiController portal = mock(FrontendApiController.class);
    AiAuditService audit = mock(AiAuditService.class);
    ExpenseService expenses = mock(ExpenseService.class);
    AiActionTokenStore tokenStore = mock(AiActionTokenStore.class);
    AiAuditRepository auditRepository = mock(AiAuditRepository.class);
    java.util.Map<String, AiActionTokenStore.Pending> previews = new java.util.concurrent.ConcurrentHashMap<>();
    AiActionService actions = new AiActionService(employees, tickets, expenses, portal, audit, tokenStore, auditRepository);

    @BeforeEach void login() {
        previews.clear();
        when(tokenStore.activeCount(anyString())).thenAnswer(inv ->
                previews.values().stream().filter(p -> p.username().equals(inv.getArgument(0))).count());
        doAnswer(inv -> {
            previews.put(inv.getArgument(0), new AiActionTokenStore.Pending(
                    inv.getArgument(1), inv.getArgument(2), inv.getArgument(3),
                    inv.getArgument(4), inv.getArgument(5), inv.getArgument(6)));
            return null;
        }).when(tokenStore).issue(anyString(), anyString(), anyString(), anyString(),
                anyMap(), any(), nullable(String.class));
        when(tokenStore.get(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            AiActionTokenStore.Pending pending = previews.get(inv.getArgument(0));
            return pending != null && pending.username().equals(inv.getArgument(1))
                    && pending.role().equals(inv.getArgument(2)) ? pending : null;
        });
        when(tokenStore.consume(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String token = inv.getArgument(0);
            AiActionTokenStore.Pending p = previews.get(token);
            return p != null && p.username().equals(inv.getArgument(1))
                    && p.role().equals(inv.getArgument(2)) && previews.remove(token, p);
        });
        when(tokenStore.status(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            AiActionTokenStore.Pending p = previews.get(inv.getArgument(0));
            if (p == null || !p.username().equals(inv.getArgument(1)) || !p.role().equals(inv.getArgument(2)))
                return null;
            return new AiActionTokenStore.TokenStatus(p.type(), "PREPARED", p.expiresAt(), null);
        });
        when(tokenStore.cancel(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String token = inv.getArgument(0);
            AiActionTokenStore.Pending p = previews.get(token);
            return p != null && p.username().equals(inv.getArgument(1))
                    && p.role().equals(inv.getArgument(2)) && previews.remove(token, p);
        });
        var account = new RegisterEmployee(); account.setUserId("employee1"); account.setRole("EMPLOYEE");
        var employee = new Employee(); employee.setFullName("Test Employee"); employee.setRegisterEmployee(account);
        when(employees.findByRegisterEmployee_UserId("employee1")).thenReturn(Optional.of(employee));
        var otherAccount = new RegisterEmployee(); otherAccount.setUserId("employee2"); otherAccount.setRole("EMPLOYEE");
        var other = new Employee(); other.setRegisterEmployee(otherAccount);
        when(employees.findByRegisterEmployee_UserId("employee2")).thenReturn(Optional.of(other));
        authenticate("employee1", "ROLE_EMPLOYEE");
    }
    private static void authenticate(String user, String role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "", List.of(new SimpleGrantedAuthority(role))));
    }
    @AfterEach void logout(){ SecurityContextHolder.clearContext(); }

    // Direct unit tests have no Spring proxy. Simulate transaction callbacks so
    // SUCCESS can only be checked after the business transaction commits.
    private AiActionService.Result confirm(AiActionService.Confirmation request) {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            AiActionService.Result result = actions.confirm(request);
            for (TransactionSynchronization hook : TransactionSynchronizationManager.getSynchronizations())
                hook.afterCommit();
            for (TransactionSynchronization hook : TransactionSynchronizationManager.getSynchronizations())
                hook.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            return result;
        } catch (RuntimeException ex) {
            for (TransactionSynchronization hook : TransactionSynchronizationManager.getSynchronizations())
                hook.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            throw ex;
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private AiActionService.Draft ticketDraft() {
        return new AiActionService.Draft("SUPPORT_TICKET", Map.of("subject","Laptop will not start",
            "description", "Laptop shows a blank screen after startup", "category","HARDWARE", "priority","HIGH"));
    }

    @Test void previewIsReadOnlyConfirmationIsOneTimeAndOwned() {
        var preview = actions.preview(ticketDraft());
        verifyNoInteractions(tickets, portal);
        assertThrows(ResponseStatusException.class, () -> confirm(new AiActionService.Confirmation(preview.token(), false)));
        authenticate("employee2", "ROLE_EMPLOYEE");
        assertThrows(ResponseStatusException.class, () -> confirm(new AiActionService.Confirmation(preview.token(), true)));
        authenticate("employee1", "ROLE_EMPLOYEE");
        var saved = new SupportTicket(); saved.setTicketNumber("INC-000042");
        when(tickets.create(any(SupportTicket.class), eq("employee1"), eq("Test Employee"))).thenReturn(saved);
        var result = confirm(new AiActionService.Confirmation(preview.token(), true));
        assertEquals("INC-000042", result.reference());
        verify(tickets, times(1)).create(any(SupportTicket.class), eq("employee1"), eq("Test Employee"));
        assertThrows(ResponseStatusException.class, () -> confirm(new AiActionService.Confirmation(preview.token(), true)));
    }

    @Test void receiptFailureFailsBusinessTransactionAndNeverClaimsSuccess() {
        var preview = actions.preview(ticketDraft());
        var saved = new SupportTicket(); saved.setTicketNumber("INC-000088");
        when(tickets.create(any(SupportTicket.class), eq("employee1"), eq("Test Employee"))).thenReturn(saved);
        doThrow(new IllegalStateException("Simulated receipt write failure"))
                .when(tokenStore).recordCommit(eq(preview.token()), eq("employee1"), eq("ROLE_EMPLOYEE"), eq("INC-000088"));
        // The @Transactional production proxy rolls back the business operation.
        // Here a mock transaction checks that failure propagates before commit callbacks.
        assertThrows(IllegalStateException.class,
                () -> confirm(new AiActionService.Confirmation(preview.token(), true)));
        verify(tickets, times(1)).create(any(SupportTicket.class), eq("employee1"), eq("Test Employee"));
        verify(audit).record("ACTION_CONFIRM_SUPPORT_TICKET", "FAILED");
        verify(audit, never()).record("ACTION_CONFIRM_SUPPORT_TICKET", "SUCCESS");
        assertThrows(ResponseStatusException.class,
                () -> confirm(new AiActionService.Confirmation(preview.token(), true)));
    }

    @Test void confirmationWithoutManagedTransactionFailsClosed() {
        var preview = actions.preview(ticketDraft());
        var saved = new SupportTicket(); saved.setTicketNumber("INC-000090");
        when(tickets.create(any(SupportTicket.class), eq("employee1"), eq("Test Employee"))).thenReturn(saved);
        assertThrows(IllegalStateException.class,
                () -> actions.confirm(new AiActionService.Confirmation(preview.token(), true)));
        verify(audit, never()).record("ACTION_CONFIRM_SUPPORT_TICKET", "SUCCESS");
    }

    @Test void verifiedCommitReceiptGeneratesSuccessAudit() {
        var preview = actions.preview(ticketDraft());
        var saved = new SupportTicket(); saved.setTicketNumber("INC-000089");
        when(tickets.create(any(SupportTicket.class), eq("employee1"), eq("Test Employee"))).thenReturn(saved);
        confirm(new AiActionService.Confirmation(preview.token(), true));
        verify(tokenStore).recordCommit(preview.token(), "employee1", "ROLE_EMPLOYEE", "INC-000089");
        verify(audit).record("ACTION_CONFIRM_SUPPORT_TICKET", "SUCCESS");
        verify(audit, never()).record("ACTION_CONFIRM_SUPPORT_TICKET", "RECEIPT_UNVERIFIED");
    }

    @Test void previewRejectsClientOwnerStatusAndUnexpectedFields() {
        assertThrows(ResponseStatusException.class, () -> actions.preview(new AiActionService.Draft("SUPPORT_TICKET",
            Map.of("subject","Laptop will not start", "description","Laptop is not turning on", "userId","admin"))));
    }
    @Test void invalidDataIsRejectedBeforeExecution() {
        assertThrows(ResponseStatusException.class, () -> actions.preview(new AiActionService.Draft("TIMESHEET_ENTRY",
            Map.of("date","2026-01-01", "hoursWorked","999", "taskDescription","Some task", "workMode","OFFICE"))));
        assertThrows(ResponseStatusException.class, () -> actions.preview(new AiActionService.Draft("LEAVE_REQUEST",
            Map.of("startDate","2027-01-03", "endDate","2027-01-01", "leaveType","Casual Leave",
                "leaveDurationType","FULL_DAY", "reason","Family work"))));
        verifyNoInteractions(tickets, portal);
    }
    @Test void staleRoleIsDenied() {
        authenticate("employee1", "ROLE_ADMIN");
        assertThrows(AccessDeniedException.class, () -> actions.preview(ticketDraft()));
    }

    @Test void expenseClaimIsReadOnlyUntilConfirmationAndIsAccountBound() {
        var draft = new AiActionService.Draft("EXPENSE_CLAIM", Map.of(
            "category", "Travel", "amount", "2500.50", "expenseDate", java.time.LocalDate.now().minusDays(1).toString(),
            "description", "Taxi fare to client office", "receiptName", "receipt-432.pdf"));
        var preview = actions.preview(draft);
        assertEquals("2500.5", preview.fields().get("amount"));
        verifyNoInteractions(expenses);
        var saved = new EmployeeManagementSystem.entity.ExpenseClaim(); saved.setId(199L);
        when(expenses.create(any(), eq("employee1"), eq("Test Employee"))).thenReturn(saved);
        var result = confirm(new AiActionService.Confirmation(preview.token(), true));
        assertEquals("199", result.reference());
        verify(expenses).create(argThat(c -> c.getId()==null && c.getUserId()==null &&
              "Travel".equals(c.getCategory()) && c.getAmount() == 2500.5), eq("employee1"), eq("Test Employee"));
        assertThrows(ResponseStatusException.class,
                () -> confirm(new AiActionService.Confirmation(preview.token(), true)));
    }
    @Test void expenseRejectsOwnerInjectionAndInvalidAmountsOrFutureDates() {
        var valid = Map.of("category", "Food", "amount", "125.50",
                "expenseDate", java.time.LocalDate.now().toString(), "description", "Meals while visiting customer",
                "receiptName", "");
        for (String amount : List.of("-10", "0", "NaN", "1000001", "20.999", "1e5")) {
            var fields = new java.util.HashMap<>(valid); fields.put("amount", amount);
            assertThrows(ResponseStatusException.class, () -> actions.preview(new AiActionService.Draft("EXPENSE_CLAIM", fields)));
        }
        var fields = new java.util.HashMap<>(valid); fields.put("userId", "admin");
        assertThrows(ResponseStatusException.class, () -> actions.preview(new AiActionService.Draft("EXPENSE_CLAIM", fields)));
        fields.remove("userId"); fields.put("expenseDate", java.time.LocalDate.now().plusDays(1).toString());
        assertThrows(ResponseStatusException.class, () -> actions.preview(new AiActionService.Draft("EXPENSE_CLAIM", fields)));
        verifyNoInteractions(expenses);
    }
    @Test void cancelReleasesPreviewAndDoesNotPermitCrossAccountCancellation() {
        var preview = actions.preview(ticketDraft());
        authenticate("employee2", "ROLE_EMPLOYEE");
        assertDoesNotThrow(() -> actions.cancel(new AiActionService.Cancellation(preview.token())));
        verify(tokenStore, never()).cancel(eq(preview.token()), eq("employee2"), anyString());
        authenticate("employee1", "ROLE_EMPLOYEE");
        actions.cancel(new AiActionService.Cancellation(preview.token()));
        actions.cancel(new AiActionService.Cancellation(preview.token())); // safe repeat
        verify(tokenStore, never()).get(eq(preview.token()), eq("employee2"), anyString());
        assertThrows(ResponseStatusException.class,
                () -> confirm(new AiActionService.Confirmation(preview.token(), true)));
        verifyNoInteractions(tickets);
    }

    @Test void statusLookupNeedsAuthenticatedOwnerAndCannotAssertCommit() {
        var preview = actions.preview(ticketDraft());
        when(tokenStore.status(eq(preview.token()), eq("employee1"), eq("ROLE_EMPLOYEE")))
                .thenReturn(new AiActionTokenStore.TokenStatus("SUPPORT_TICKET", "CONSUMED", java.time.Instant.now(), null));
        var status = actions.status(preview.token());
        assertEquals("CONSUMED", status.state());
        assertTrue(status.guidance().contains("not verified"));
        authenticate("employee2", "ROLE_EMPLOYEE");
        assertThrows(ResponseStatusException.class, () -> actions.status(preview.token()));
        verify(tokenStore).status(preview.token(), "employee2", "ROLE_EMPLOYEE");
    }

    @Test void monitoringRequiresRealAdminAndAllowsOnlyFixedWindows() {
        assertThrows(AccessDeniedException.class, () -> actions.monitor(24));
        reviewer("admin1", "ADMIN");
        when(tokenStore.monitor(any(), any())).thenReturn(new AiActionTokenStore.MonitorCounts(3, 4, 5, 6, 2));
        when(auditRepository.countByEventTypeStartingWithAndResultAndOccurredAtAfter(eq("ACTION_CONFIRM_"), eq("SUCCESS"), any()))
                .thenReturn(7L);
        when(auditRepository.countByEventTypeStartingWithAndResultAndOccurredAtAfter(eq("ACTION_CONFIRM_"), eq("FAILED"), any()))
                .thenReturn(2L);
        when(auditRepository.countByEventTypeStartingWithAndResultAndOccurredAtAfter(eq("ACTION_CONFIRM_"), eq("RECEIPT_UNVERIFIED"), any()))
                .thenReturn(1L);
        var summary = actions.monitor(24);
        assertEquals(1L, summary.receiptUnverifiedEvents());
        assertEquals(3L, summary.activePreviews());
        assertEquals(5L, summary.consumedAttempts());
        assertEquals(7L, summary.successfulCommitEvents());
        assertEquals(2L, summary.failedCommitEvents());
        assertEquals(2L, summary.recordedCommits());
        assertThrows(ResponseStatusException.class, () -> actions.monitor(9999));
    }

    @Test void committedStatusRequiresServerSideReceiptAndExposesOnlyOwnReference() {
        when(tokenStore.status("owned-confirmation", "employee1", "ROLE_EMPLOYEE"))
            .thenReturn(new AiActionTokenStore.TokenStatus("SUPPORT_TICKET", "COMMITTED",
                java.time.Instant.now(), "INC-0092"));
        var status = actions.status("owned-confirmation");
        assertEquals("COMMITTED", status.state());
        assertEquals("INC-0092", status.reference());
        assertTrue(status.guidance().contains("transaction committed"));
        authenticate("employee2", "ROLE_EMPLOYEE");
        assertThrows(ResponseStatusException.class, () -> actions.status("owned-confirmation"));
    }

    private void reviewer(String name, String role) {
        var account = new RegisterEmployee(); account.setUserId(name); account.setRole(role);
        var employee = new Employee(); employee.setRegisterEmployee(account); employee.setFullName("Authorized Reviewer");
        when(employees.findByRegisterEmployee_UserId(name)).thenReturn(Optional.of(employee));
        authenticate(name, "ROLE_" + role);
    }
    private static Map<String,Object> row(String owner, String status, boolean allowed) {
        return Map.of("id", 19L, "employeeId", owner, "employeeName", "Employee E", "status", status,
                "canReview", allowed, "leaveType", "Casual Leave", "startDate", "2026-11-01", "endDate", "2026-11-02");
    }
    private static AiActionService.Draft reviewDraft(String decision) {
        return new AiActionService.Draft("REVIEW_LEAVE", Map.of("recordId", "19", "decision", decision, "comment", "Reviewed policy and staffing"));
    }
    @Test void employeeCannotSeeOrApproveQueue() {
        assertThrows(AccessDeniedException.class, () -> actions.reviewQueue());
        assertThrows(AccessDeniedException.class, () -> actions.preview(reviewDraft("APPROVED")));
        verify(portal, never()).updateLeave(any(), anyLong(), anyMap());
    }
    @Test void managerOnlyPreviewsAssignedPendingReviews() {
        reviewer("manager1", "MANAGER");
        when(portal.teamLeaves(any())).thenReturn(List.of(row("employee1", "PENDING", false)));
        assertTrue(actions.reviewQueue().isEmpty());
        assertThrows(AccessDeniedException.class, () -> actions.preview(reviewDraft("APPROVED")));
        when(portal.teamLeaves(any())).thenReturn(List.of(row("employee1", "APPROVED", true)));
        assertThrows(AccessDeniedException.class, () -> actions.preview(reviewDraft("APPROVED")));
        verify(portal, never()).updateLeave(any(), anyLong(), anyMap());
    }
    @Test void reviewRequiresExplicitConsentIsOneTimeAndDelegatesToExistingWorkflow() {
        reviewer("manager1", "MANAGER");
        when(portal.teamLeaves(any())).thenReturn(List.of(row("employee1", "PENDING", true)));
        var preview = actions.preview(reviewDraft("REJECTED"));
        assertEquals(1, actions.reviewQueue().size());
        verify(portal, never()).updateLeave(any(), anyLong(), anyMap());
        assertThrows(ResponseStatusException.class, () -> confirm(new AiActionService.Confirmation(preview.token(), false)));
        var result = confirm(new AiActionService.Confirmation(preview.token(), true));
        assertEquals("REJECTED", result.status());
        verify(portal).updateLeave(any(), eq(19L), argThat(m -> "REJECTED".equals(m.get("status"))));
        assertThrows(ResponseStatusException.class, () -> confirm(new AiActionService.Confirmation(preview.token(), true)));
    }
    @Test void staleReviewOwnershipIsRejectedAtConfirmation() {
        reviewer("hr1", "HR");
        when(portal.allLeaves(any())).thenReturn(List.of(row("employee1", "PENDING", true)))
                .thenReturn(List.of(row("employee2", "PENDING", true)));
        var preview = actions.preview(reviewDraft("APPROVED"));
        assertThrows(AccessDeniedException.class, () -> confirm(new AiActionService.Confirmation(preview.token(), true)));
        verify(portal, never()).updateLeave(any(), anyLong(), anyMap());
    }
    @Test void unexpectedReviewFieldsOrMissingRationaleFailClosed() {
        reviewer("manager1", "MANAGER");
        assertThrows(ResponseStatusException.class, () -> actions.preview(new AiActionService.Draft("REVIEW_LEAVE",
                Map.of("recordId","19", "decision","APPROVED", "comment","Good", "employeeId","employee1"))));
        assertThrows(ResponseStatusException.class, () -> actions.preview(new AiActionService.Draft("REVIEW_LEAVE",
                Map.of("recordId","19", "decision","REJECTED", "comment",""))));
    }
    @Test void timesheetReviewUsesExistingWorkflow() {
        reviewer("manager1", "MANAGER");
        when(portal.teamTimesheets(any())).thenReturn(List.of(Map.of("id", 21L, "employeeId", "employee1", "employeeName", "Employee E",
                "status", "PENDING", "canReview", true, "date", "2026-10-20", "hoursWorked", 8)));
        var preview = actions.preview(new AiActionService.Draft("REVIEW_TIMESHEET",
                Map.of("recordId", "21", "decision", "APPROVED", "comment", "Hours and assignment checked")));
        confirm(new AiActionService.Confirmation(preview.token(), true));
        verify(portal).updateTimesheet(any(), eq(21L), argThat(m -> "APPROVED".equals(m.get("status"))));
    }
}
