package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * V22 phase-one portal readers. Inputs MUST be the already authenticated, database-verified
 * account ID and assigned role passed by AiAssistantService. No user-supplied IDs are trusted.
 * No mutating repository methods are used and raw entities are never sent to the LLM.
 */
@Service
public class AiPortalInsightsService {
    private final SupportTicketRepository tickets;
    private final EmployeeAssetRepository assets;
    private final EmployeeAssetRequestRepository assetRequests;
    private final ExpenseClaimRepository expenses;
    private final TrainingEnrollmentRepository enrollments;
    private final TrainingCourseRepository courses;
    private final PerformanceReviewRepository reviews;
    private final JobOpeningRepository jobs;
    private final PortalMeetingRepository meetings;

    public AiPortalInsightsService(SupportTicketRepository tickets, EmployeeAssetRepository assets,
            EmployeeAssetRequestRepository assetRequests, ExpenseClaimRepository expenses,
            TrainingEnrollmentRepository enrollments, TrainingCourseRepository courses,
            PerformanceReviewRepository reviews, JobOpeningRepository jobs,
            PortalMeetingRepository meetings) {
        this.tickets = tickets;
        this.assets = assets;
        this.assetRequests = assetRequests;
        this.expenses = expenses;
        this.enrollments = enrollments;
        this.courses = courses;
        this.reviews = reviews;
        this.jobs = jobs;
        this.meetings = meetings;
    }

    @Transactional(readOnly = true)
    public String answer(String question, String accountId, String verifiedRole, Employee me) {
        AiPortalIntent.Module module = AiPortalIntent.detect(question);
        if (module == AiPortalIntent.Module.NONE) return null;
        if (accountId == null || accountId.isBlank() || verifiedRole == null || me == null)
            throw new org.springframework.security.access.AccessDeniedException("Verified employee session required");
        String q = question.toLowerCase(Locale.ROOT);
        boolean teamOrOther = q.matches("(?s).*(my team|team members|direct reports|all employees|everyone|another employee|other employee|colleague|employee [a-z0-9]+).*");
        return switch (module) {
            case SUPPORT -> teamOrOther ? selfOnly("support tickets") : support(accountId);
            case ASSETS -> teamOrOther ? selfOnly("assets") : assets(accountId);
            case EXPENSES -> teamOrOther ? selfOnly("expense claims") : expenses(accountId);
            case TRAINING -> teamOrOther ? selfOnly("training enrollments") : training(accountId, q);
            case PERFORMANCE -> performance(accountId, verifiedRole, me, teamOrOther, q);
            case RECRUITMENT -> recruitment(me, verifiedRole, q);
            case MEETINGS -> meetings(q);
            default -> null;
        };
    }

    /** Cross-module self snapshot: only account-owned rows; excludes salary and other staff. */
    @Transactional(readOnly = true)
    public String selfOverview(String accountId, String verifiedRole, Employee me) {
        if (accountId == null || accountId.isBlank() || verifiedRole == null || me == null)
            throw new org.springframework.security.access.AccessDeniedException("Verified employee session required");
        List<SupportTicket> myTickets = tickets.findByUserIdOrderByUpdatedAtDesc(accountId);
        List<EmployeeAsset> myAssets = assets.findByAssignedUserIdOrderByUpdatedAtDesc(accountId);
        List<EmployeeAssetRequest> myAssetRequests = assetRequests.findByUserIdOrderByCreatedAtDesc(accountId);
        List<ExpenseClaim> myExpenses = expenses.findByUserIdOrderByExpenseDateDesc(accountId);
        List<TrainingEnrollment> myTraining = enrollments.findByUserIdOrderByEnrolledAtDesc(accountId);
        List<PerformanceReview> myReviews = reviews.findByUserIdOrderByUpdatedAtDesc(accountId);
        long openTickets = myTickets.stream().filter(t -> !"CLOSED".equalsIgnoreCase(t.getStatus())
                && !"RESOLVED".equalsIgnoreCase(t.getStatus())).count();
        long pendingExpenses = myExpenses.stream().filter(e -> "PENDING".equalsIgnoreCase(e.getStatus())).count();
        return "[Additional portal modules — your account, all-time]"
                + "\nSupport tickets: " + myTickets.size() + " total, " + openTickets + " not closed/resolved."
                + "\nAssigned assets: " + myAssets.size() + "; asset requests: " + myAssetRequests.size() + "."
                + "\nExpense claims: " + myExpenses.size() + " total, " + pendingExpenses + " pending."
                + "\nTraining enrollments: " + myTraining.size() + "."
                + "\nPerformance reviews: " + myReviews.size() + "."
                + "\nSource: authorized, read-only portal module records. Figures are based on stored statuses; "
                + "no pay amounts or other employees' private records are shown.";
    }

    private String selfOnly(String area) {
        return "This AI view currently shows only your own " + area + ". To review other employees, use the permission-controlled portal module.";
    }

    private String support(String id) {
        List<SupportTicket> rows = tickets.findByUserIdOrderByUpdatedAtDesc(id);
        if (rows.isEmpty()) return "No support tickets are linked to your account.";
        return "Your support tickets (" + rows.size() + " total; latest 8):\n" + rows.stream().limit(8)
                .map(t -> "- " + safe(t.getTicketNumber(), 35) + ": " + safe(t.getSubject(), 85)
                        + " | " + safe(t.getStatus(), 24) + " | priority " + safe(t.getPriority(), 24))
                .collect(Collectors.joining("\n")) + "\nOpen Support Tickets to view details or take action.";
    }

    private String assets(String id) {
        List<EmployeeAsset> assigned = assets.findByAssignedUserIdOrderByUpdatedAtDesc(id);
        List<EmployeeAssetRequest> requests = assetRequests.findByUserIdOrderByCreatedAtDesc(id);
        String assignedText = assigned.stream().limit(8).map(a -> "- " + safe(a.getAssetName(), 85)
                + " (" + safe(a.getAssetTag(), 45) + "), status " + safe(a.getAssetStatus(), 24))
                .collect(Collectors.joining("\n"));
        String requestText = requests.stream().limit(5).map(r -> "- " + safe(r.getAssetType(), 60)
                + ": " + safe(r.getStatus(), 24)).collect(Collectors.joining("\n"));
        return "Your assigned assets (" + assigned.size() + "):\n"
                + (assignedText.isEmpty() ? "No assigned assets." : assignedText)
                + "\nYour asset requests (" + requests.size() + "):\n"
                + (requestText.isEmpty() ? "No asset requests." : requestText);
    }

    private String expenses(String id) {
        List<ExpenseClaim> rows = expenses.findByUserIdOrderByExpenseDateDesc(id);
        if (rows.isEmpty()) return "No expense claims are linked to your account.";
        return "Your expense claims (" + rows.size() + " total; latest 8):\n" + rows.stream().limit(8)
                .map(e -> "- " + e.getExpenseDate() + " | " + safe(e.getCategory(), 50)
                        + " | amount " + (e.getAmount() == null ? "unavailable" : e.getAmount())
                        + " | " + safe(e.getStatus(), 24)).collect(Collectors.joining("\n"))
                + "\nCheck Expenses for the official claim and payment details.";
    }

    private String training(String id, String question) {
        if (question.contains("available") || question.contains("catalog") || question.contains("published")) {
            List<TrainingCourse> published = courses.findByStatusOrderByStartDateDesc("PUBLISHED");
            if (published.isEmpty()) return "No published training courses are available.";
            return "Published training courses (latest 10):\n" + published.stream().limit(10)
                    .map(c -> "- " + safe(c.getTitle(), 110) + " | " + safe(c.getCategory(), 40)
                            + " | starts " + String.valueOf(c.getStartDate()))
                    .collect(Collectors.joining("\n"));
        }
        List<TrainingEnrollment> rows = enrollments.findByUserIdOrderByEnrolledAtDesc(id);
        if (rows.isEmpty()) return "No training enrollments are linked to your account. Ask for available training courses to view the catalog.";
        return "Your training enrollments (" + rows.size() + " total; latest 8):\n" + rows.stream().limit(8)
                .map(e -> "- " + (e.getCourse() == null ? "Course" : safe(e.getCourse().getTitle(), 100))
                        + " | " + safe(e.getStatus(), 24) + " | progress "
                        + (e.getProgressPercent() == null ? "not recorded" : e.getProgressPercent() + "%"))
                .collect(Collectors.joining("\n"));
    }

    private String performance(String id, String role, Employee me, boolean team, String q) {
        List<PerformanceReview> rows;
        if (team) {
            if (!"ROLE_MANAGER".equals(role) || me.getId() == null)
                return "Team performance is available in AI only to the verified direct-report manager. Use the Performance module for other permitted views.";
            rows = reviews.findByEmployee_ReportingManager_IdOrderByUpdatedAtDesc(me.getId());
        } else {
            rows = reviews.findByUserIdOrderByUpdatedAtDesc(id);
        }
        if (rows.isEmpty()) return team ? "No direct-report performance reviews found." : "No performance reviews are linked to your account.";
        return (team ? "Direct-report performance reviews" : "Your performance reviews") + " (" + rows.size() + " total; latest 8):\n"
                + rows.stream().limit(8).map(r -> "- " + (team ? safe(r.getEmployeeName(), 60) + " | " : "")
                        + safe(r.getCycle(), 50) + " | " + safe(r.getStatus(), 30)
                        + " | due " + String.valueOf(r.getDueDate()))
                        .collect(Collectors.joining("\n"))
                + "\nReview full feedback in the protected Performance module.";
    }

    private String recruitment(Employee me, String role, String q) {
        // Mirrors the published-openings scope of /api/recruitment/openings.
        List<JobOpening> rows;
        if ("ROLE_MANAGER".equals(role)) {
            String department = me.getDepartment() == null ? null : me.getDepartment().getDepartmentName();
            if (department == null || department.isBlank()) return "Your department is not configured, so I cannot show department-scoped vacancies.";
            rows = jobs.findByStatusAndDepartmentIgnoreCaseOrderByCreatedAtDesc("OPEN", department);
        } else rows = jobs.findByStatusOrderByCreatedAtDesc("OPEN");
        if (rows.isEmpty()) return "No published open positions are visible to your account.";
        return "Published open positions (" + rows.size() + " total; latest 10):\n" + rows.stream().limit(10)
                .map(j -> "- " + safe(j.getTitle(), 100) + " | " + safe(j.getDepartment(), 50)
                        + " | " + safe(j.getLocation(), 55))
                .collect(Collectors.joining("\n"))
                + "\nCandidate records and internal recruitment decisions are not exposed through this AI view.";
    }

    private String meetings(String q) {
        // The existing meeting calendar endpoint is visible to authenticated portal users.
        // This is a shared calendar, not an attendee-specific 'my meetings' lookup.
        if (q.contains("my meeting") || q.contains("meetings assigned") || q.contains("my calendar")) {
            return "The portal meeting calendar is shared and does not track attendee-specific access here. I cannot verify which meetings you are personally invited to.";
        }
        OffsetDateTime from = OffsetDateTime.now();
        List<PortalMeeting> rows = meetings.findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(from, from.plusDays(30));
        if (rows.isEmpty()) return "No shared portal meetings are scheduled in the next 30 days.";
        return "Shared portal meetings in the next 30 days (latest 10):\n" + rows.stream().limit(10)
                .map(m -> "- " + safe(m.title, 90) + " | " + m.startsAt
                        + " | " + safe(m.status, 24)).collect(Collectors.joining("\n"))
                + "\nThese are shared calendar entries, not proof of your attendance or invitation.";
    }

    private static String safe(String value, int max) {
        if (value == null || value.isBlank()) return "not recorded";
        String cleaned = value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() > max ? cleaned.substring(0, max) + "..." : cleaned;
    }
}
