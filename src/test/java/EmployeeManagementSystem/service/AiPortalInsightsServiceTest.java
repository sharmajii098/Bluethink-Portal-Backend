package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiPortalInsightsServiceTest {
    private final SupportTicketRepository tickets = mock(SupportTicketRepository.class);
    private final EmployeeAssetRepository assets = mock(EmployeeAssetRepository.class);
    private final EmployeeAssetRequestRepository requests = mock(EmployeeAssetRequestRepository.class);
    private final ExpenseClaimRepository expenses = mock(ExpenseClaimRepository.class);
    private final TrainingEnrollmentRepository enrollments = mock(TrainingEnrollmentRepository.class);
    private final TrainingCourseRepository courses = mock(TrainingCourseRepository.class);
    private final PerformanceReviewRepository reviews = mock(PerformanceReviewRepository.class);
    private final JobOpeningRepository jobs = mock(JobOpeningRepository.class);
    private final PortalMeetingRepository meetings = mock(PortalMeetingRepository.class);
    private AiPortalInsightsService service;
    private Employee signedIn;

    @BeforeEach void setup() {
        service = new AiPortalInsightsService(tickets, assets, requests, expenses, enrollments, courses, reviews, jobs, meetings);
        signedIn = new Employee();
        signedIn.setId(42L);
    }

    @Test void ticketsAreReadOnlyAndAccountScoped() {
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber("BT-42"); ticket.setSubject("My laptop"); ticket.setStatus("OPEN");
        when(tickets.findByUserIdOrderByUpdatedAtDesc("EMP0042")).thenReturn(List.of(ticket));
        String response = service.answer("Show my support tickets", "EMP0042", "ROLE_EMPLOYEE", signedIn);
        assertTrue(response.contains("BT-42"));
        verify(tickets).findByUserIdOrderByUpdatedAtDesc("EMP0042");
        verify(tickets, never()).findAll();
    }

    @Test void otherEmployeesExpenseRequestsNeverQueryDatabase() {
        String response = service.answer("Show all employees' expense claims", "EMP0042", "ROLE_HR", signedIn);
        assertTrue(response.contains("only your own"));
        verifyNoInteractions(expenses);
    }

    @Test void onlyManagersMayFetchDirectReportPerformance() {
        String denied = service.answer("Show my team's performance reviews", "EMP0042", "ROLE_EMPLOYEE", signedIn);
        assertTrue(denied.contains("direct-report manager"));
        verifyNoInteractions(reviews);
        when(reviews.findByEmployee_ReportingManager_IdOrderByUpdatedAtDesc(42L)).thenReturn(List.of());
        service.answer("Show my team's performance reviews", "EMP0042", "ROLE_MANAGER", signedIn);
        verify(reviews).findByEmployee_ReportingManager_IdOrderByUpdatedAtDesc(42L);
    }

    @Test void refusesUnverifiedCaller() {
        assertThrows(AccessDeniedException.class, () -> service.answer("Show my tickets", "", "ROLE_EMPLOYEE", signedIn));
        verifyNoInteractions(tickets);
    }
    @Test void personalOverviewReadsOnlyAuthenticatedAccounts() {
        String output = service.selfOverview("EMP0042", "ROLE_EMPLOYEE", signedIn);
        assertTrue(output.contains("Additional portal modules"));
        verify(tickets).findByUserIdOrderByUpdatedAtDesc("EMP0042");
        verify(expenses).findByUserIdOrderByExpenseDateDesc("EMP0042");
        verify(assets).findByAssignedUserIdOrderByUpdatedAtDesc("EMP0042");
        verify(reviews).findByUserIdOrderByUpdatedAtDesc("EMP0042");
        verify(tickets, never()).findAll();
    }

}
