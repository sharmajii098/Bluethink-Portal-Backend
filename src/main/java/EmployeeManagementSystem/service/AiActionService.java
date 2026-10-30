package EmployeeManagementSystem.service;

import EmployeeManagementSystem.controller.FrontendApiController;
import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.entity.ExpenseClaim;
import EmployeeManagementSystem.entity.LeaveRequest;
import EmployeeManagementSystem.entity.SupportTicket;
import EmployeeManagementSystem.entity.HrServiceRequest;
import EmployeeManagementSystem.entity.OvertimeRequest;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.repository.AiAuditRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * V22.7: explicit, human-confirmed self-service and reviewer actions. Never executes LLM text as a command.
 * Preview tokens are durable, actor-bound and atomically consumed using the database.
 * V23.2: completion receipts join the business transaction, so a committed workflow
 * cannot be separated from its receipt by an after-commit persistence failure.
 */
@Service
public class AiActionService {
    private static final Logger log = LoggerFactory.getLogger(AiActionService.class);
    private static final int MAX_PENDING_PER_ACCOUNT = 8;
    private static final long TTL_SECONDS = 600;
    private static final Set<String> TICKET_CATEGORIES = Set.of("IT SUPPORT", "ACCESS & ACCOUNTS", "HARDWARE", "SOFTWARE", "NETWORK", "HR / PAYROLL", "OTHER");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final Set<String> DURATIONS = Set.of("FULL_DAY", "FIRST_HALF", "SECOND_HALF");
    private static final Set<String> EXPENSE_CATEGORIES = Set.of("Travel", "Food", "Accommodation", "Medical", "Office Supplies", "Internet/Phone", "Other");
    private static final Set<String> TYPES = Set.of("SUPPORT_TICKET", "LEAVE_REQUEST", "TIMESHEET_ENTRY", "EXPENSE_CLAIM", "HR_SERVICE_REQUEST", "OVERTIME_REQUEST", "REVIEW_LEAVE", "REVIEW_TIMESHEET");
    private final SecureRandom random = new SecureRandom();
    private final EmployeeRepository employees;
    private final SupportTicketService tickets;
    private final ExpenseService expenses;
    private final FrontendApiController portal;
    private final AiAuditService audit;
    private final AiActionTokenStore tokenStore;
    private final AiAuditRepository auditRepository;
    private final HrServiceRequestService hrRequests;
    private final OvertimeRequestService overtimeRequests;

    // Legacy constructor supports existing direct unit tests; production Spring wiring uses the annotated constructor.
    public AiActionService(EmployeeRepository employees, SupportTicketService tickets, ExpenseService expenses,
                           FrontendApiController portal, AiAuditService audit, AiActionTokenStore tokenStore, AiAuditRepository auditRepository) {
        this(employees, tickets, expenses, portal, audit, tokenStore, auditRepository, null, null);
    }

    @Autowired
    public AiActionService(EmployeeRepository employees, SupportTicketService tickets, ExpenseService expenses,
                           FrontendApiController portal, AiAuditService audit, AiActionTokenStore tokenStore,
                           AiAuditRepository auditRepository, HrServiceRequestService hrRequests,
                           OvertimeRequestService overtimeRequests) {
        this.hrRequests = hrRequests;
        this.overtimeRequests = overtimeRequests;
        this.employees = employees;
        this.tickets = tickets;
        this.expenses = expenses;
        this.portal = portal;
        this.audit = audit;
        this.tokenStore = tokenStore;
        this.auditRepository = auditRepository;
    }

    public record Draft(String type, Map<String, String> fields) {}
    public record Preview(String token, String type, Map<String, String> fields, String summary, Instant expiresAt) {}
    public record Confirmation(String token, Boolean confirmed) {}
    public record Cancellation(String token) {}
    public record Result(String type, String status, String reference, String message) {}
    public record Status(String type, String state, Instant expiresAt, String guidance, String reference) {}
    public record Monitor(int windowHours, long activePreviews, long expiredPreviews,
                          long consumedAttempts, long cancelledPreviews,
                          long successfulCommitEvents, long failedCommitEvents,
                          long recordedCommits, long receiptUnverifiedEvents) {}

    /** Status is tied to the current account and role. CONSUMED never asserts business success. */
    public Status status(String token) {
        Identity me = verifiedIdentity();
        AiActionTokenStore.TokenStatus s = tokenStore.status(token, me.username(), me.role());
        if (s == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Preview unavailable for this account.");
        String guidance = switch (s.state()) {
            case "PREPARED" -> "This preview has not been submitted. You may cancel it. Do not blindly repeat a previous confirmation.";
            case "CONSUMED" -> "A submission was attempted but a committed result is not verified. Check the relevant portal module or contact an administrator before creating another request.";
            case "COMMITTED" -> "The server recorded a receipt after the business transaction committed. Verify the final record in the relevant portal module.";
            case "CANCELLED" -> "This draft was cancelled and cannot be submitted.";
            default -> "This preview expired and cannot be submitted.";
        };
        return new Status(s.type(), s.state(), s.expiresAt(), guidance, s.reference());
    }

    /** Private, read-only recovery evidence after refresh when the confirmation token is no longer in the tab. */
    public List<AiActionTokenStore.RecentAction> recentActions() {
        Identity me = verifiedIdentity();
        return tokenStore.recentForAccount(me.username(), me.role());
    }

    /** A JWT admin claim alone is insufficient: verify the currently assigned HRMS role. */
    public void requireAdminForDiagnostics() {
        Identity me = verifiedIdentity();
        if (!"ROLE_ADMIN".equals(me.role())) throw new AccessDeniedException("Admin access required.");
    }

    /** Aggregate diagnostics only. Requires verified admin identity; never exposes action fields or user identifiers. */
    public Monitor monitor(int hours) {
        Identity me = verifiedIdentity();
        if (!"ROLE_ADMIN".equals(me.role())) throw new AccessDeniedException("Admin access required.");
        if (hours != 24 && hours != 168) throw bad("Window must be 24 or 168 hours.");
        Instant now = Instant.now();
        Instant since = now.minusSeconds(hours * 3600L);
        AiActionTokenStore.MonitorCounts c = tokenStore.monitor(since, now);
        return new Monitor(hours, c.active(), c.expired(), c.consumed(), c.cancelled(),
                auditRepository.countByEventTypeStartingWithAndResultAndOccurredAtAfter("ACTION_CONFIRM_", "SUCCESS", since),
                auditRepository.countByEventTypeStartingWithAndResultAndOccurredAtAfter("ACTION_CONFIRM_", "FAILED", since),
                c.recordedCommits(),
                auditRepository.countByEventTypeStartingWithAndResultAndOccurredAtAfter("ACTION_CONFIRM_", "RECEIPT_UNVERIFIED", since));
    }
    private record Identity(String username, String role, Employee employee, Authentication auth) {}
    public record ReviewItem(String type, String id, String employeeName, String description) {}

    /** Read only. Reviewer scope is supplied by the existing workflow endpoints, never the LLM. */
    public List<ReviewItem> reviewQueue() {
        Identity me = verifiedIdentity();
        requireReviewer(me);
        List<ReviewItem> output = new ArrayList<>();
        for (String type : List.of("REVIEW_LEAVE", "REVIEW_TIMESHEET")) {
            int added = 0;
            for (Map<String,Object> record : reviewRows(me, type)) {
                if (!reviewable(record)) continue;
                output.add(new ReviewItem(type, Objects.toString(record.get("id"), ""),
                        Objects.toString(record.get("employeeName"), "Employee"), reviewDescription(type, record)));
                if (++added >= 40) break; // Keep both queues visible even if one has a backlog.
            }
        }
        return List.copyOf(output);
    }

    private static boolean isReview(String type) { return "REVIEW_LEAVE".equals(type) || "REVIEW_TIMESHEET".equals(type); }
    private static void requireReviewer(Identity me) {
        if (!Set.of("ROLE_MANAGER", "ROLE_HR", "ROLE_ADMIN").contains(me.role()))
            throw new AccessDeniedException("Only authorized reviewers may review requests.");
    }
    private List<Map<String,Object>> reviewRows(Identity me, String type) {
        if ("REVIEW_LEAVE".equals(type))
            return "ROLE_MANAGER".equals(me.role()) ? portal.teamLeaves(me.auth()) : portal.allLeaves(me.auth());
        if ("REVIEW_TIMESHEET".equals(type))
            return "ROLE_MANAGER".equals(me.role()) ? portal.teamTimesheets(me.auth()) : portal.allTimesheets(me.auth());
        throw bad("Unsupported review type.");
    }
    private static boolean reviewable(Map<String,Object> item) {
        return Boolean.TRUE.equals(item.get("canReview")) && "PENDING".equalsIgnoreCase(Objects.toString(item.get("status"), ""));
    }
    private Map<String,Object> target(Identity me, String type, String id) {
        requireReviewer(me);
        return reviewRows(me, type).stream()
                .filter(AiActionService::reviewable)
                .filter(row -> id.equals(Objects.toString(row.get("id"), "")))
                .findFirst().orElseThrow(() -> new AccessDeniedException("Request unavailable, already reviewed, or not assigned to you."));
    }
    private static String reviewDescription(String type, Map<String,Object> r) {
        if ("REVIEW_LEAVE".equals(type)) return "Leave: " + Objects.toString(r.get("leaveType"), "")
                + " · " + Objects.toString(r.get("startDate"), "") + " to " + Objects.toString(r.get("endDate"), "");
        return "Timesheet: " + Objects.toString(r.get("date"), "") + " · "
                + Objects.toString(r.get("hoursWorked"), "") + " hours";
    }

    public Preview preview(Draft draft) {
        Identity me = verifiedIdentity();
        if (draft == null || draft.type() == null || !TYPES.contains(draft.type()))
            throw bad("Choose a supported action.");
        if ("TIMESHEET_ENTRY".equals(draft.type()) && "ROLE_ADMIN".equals(me.role()))
            throw new AccessDeniedException("Admins cannot submit timesheets.");
        Map<String,String> fields = validate(draft.type(), draft.fields());
        Map<String,Object> reviewed = isReview(draft.type()) ? target(me, draft.type(), fields.get("recordId")) : null;
        String summary = reviewed == null ? summary(draft.type(), fields)
                : ("APPROVED".equals(fields.get("decision")) ? "Approve " : "Reject ")
                + draft.type().replace("REVIEW_", "").toLowerCase(Locale.ROOT) + " #" + fields.get("recordId")
                + " for " + Objects.toString(reviewed.get("employeeName"), "Employee")
                + " · " + reviewDescription(draft.type(), reviewed);
        Instant now = Instant.now();
        if (tokenStore.activeCount(me.username()) >= MAX_PENDING_PER_ACCOUNT)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many open drafts. Confirm or wait for a preview to expire.");
        byte[] bytes = new byte[24]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiry = now.plusSeconds(TTL_SECONDS);
        tokenStore.issue(token, me.username(), me.role(), draft.type(), fields, expiry,
                reviewed == null ? null : Objects.toString(reviewed.get("employeeId"), ""));
        audit.record("ACTION_PREVIEW_" + draft.type(), "PREPARED");
        return new Preview(token, draft.type(), fields, summary, expiry);
    }

    /** Releases an unused preview; a different account cannot revoke another account's token. */
    public void cancel(Cancellation cancellation) {
        Identity me = verifiedIdentity();
        if (cancellation == null || cancellation.token() == null || cancellation.token().isBlank()
                || cancellation.token().length() > 100) throw bad("Invalid preview token.");
        // A cancellation needs only type + state, never the original draft JSON.
        AiActionTokenStore.TokenStatus action = tokenStore.status(
                cancellation.token(), me.username(), me.role());
        if (action == null || !"PREPARED".equals(action.state())) return;
        // Atomic update independently enforces account, current role and expiration.
        if (tokenStore.cancel(cancellation.token(), me.username(), me.role()))
            audit.record("ACTION_CANCEL_" + action.type(), "CANCELLED");
    }

    @Transactional
    public Result confirm(Confirmation confirmation) {
        Identity me = verifiedIdentity();
        if (confirmation == null || !Boolean.TRUE.equals(confirmation.confirmed()))
            throw bad("Explicit confirmation is required before submitting an action.");
        String token = confirmation.token();
        if (token == null || token.length() > 100) throw bad("Invalid or expired action preview.");
        AiActionTokenStore.Pending action = tokenStore.get(token, me.username(), me.role());
        if (action == null || !action.expiresAt().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Preview expired or already used. Prepare a new draft.");
        if (!action.username().equals(me.username()) || !action.role().equals(me.role()))
            throw new AccessDeniedException("This action belongs to another account or your role has changed.");
        if (!tokenStore.consume(token, me.username(), me.role()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This action has already been submitted.");
        // Never use a client-supplied employee ID, role, status, reviewer or record owner.
        Map<String,String> f = action.fields();
        try {
            // Recheck assignment, ownership and pending status after the one-time token is consumed.
            if (isReview(action.type())) {
                Map<String,Object> current = target(me, action.type(), f.get("recordId"));
                if (!Objects.equals(action.targetEmployeeId(), Objects.toString(current.get("employeeId"), "")))
                    throw new AccessDeniedException("Request ownership changed. Prepare a new preview.");
            }
            Result result;
            switch (action.type()) {
                case "SUPPORT_TICKET" -> {
                    SupportTicket ticket = new SupportTicket();
                    ticket.setSubject(f.get("subject"));
                    ticket.setDescription(f.get("description"));
                    ticket.setCategory(f.get("category"));
                    ticket.setPriority(f.get("priority"));
                    SupportTicket saved = tickets.create(ticket, me.username(),
                            me.employee().getFullName() == null || me.employee().getFullName().isBlank()
                                    ? me.username() : me.employee().getFullName());
                    result = new Result(action.type(), "SUBMITTED", saved.getTicketNumber(), "Support ticket created.");
                }
                case "HR_SERVICE_REQUEST" -> {
                    if (hrRequests == null) throw new IllegalStateException("HR service requests unavailable");
                    HrServiceRequest request = new HrServiceRequest();
                    request.setCategory(f.get("category"));
                    request.setSubject(f.get("subject"));
                    request.setDescription(f.get("description"));
                    request.setPriority(f.get("priority"));
                    HrServiceRequest saved = hrRequests.create(request, me.username(),
                            me.employee().getFullName() == null || me.employee().getFullName().isBlank()
                                    ? me.username() : me.employee().getFullName());
                    result = new Result(action.type(), "SUBMITTED", saved.getRequestNumber(),
                            "HR service request submitted for normal review.");
                }
                case "OVERTIME_REQUEST" -> {
                    if (overtimeRequests == null) throw new IllegalStateException("Overtime service unavailable");
                    OvertimeRequest request = new OvertimeRequest();
                    request.setWorkDate(LocalDate.parse(f.get("workDate")));
                    request.setHours(Double.valueOf(f.get("hours")));
                    request.setProject(f.get("project"));
                    request.setReason(f.get("reason"));
                    request.setCompensationMode(f.get("compensationMode"));
                    OvertimeRequest saved = overtimeRequests.create(request, me.username(),
                            me.employee().getFullName() == null || me.employee().getFullName().isBlank()
                                    ? me.username() : me.employee().getFullName());
                    result = new Result(action.type(), "SUBMITTED", saved.getRequestNumber(),
                            "Overtime request submitted for normal approval.");
                }
                case "EXPENSE_CLAIM" -> {
                    ExpenseClaim claim = new ExpenseClaim();
                    claim.setCategory(f.get("category"));
                    claim.setAmount(Double.valueOf(f.get("amount")));
                    claim.setExpenseDate(LocalDate.parse(f.get("expenseDate")));
                    claim.setDescription(f.get("description"));
                    claim.setReceiptName(f.get("receiptName")); // Reference only; not a file upload.
                    ExpenseClaim saved = expenses.create(claim, me.username(),
                            me.employee().getFullName() == null || me.employee().getFullName().isBlank()
                                    ? me.username() : me.employee().getFullName());
                    result = new Result(action.type(), "SUBMITTED", String.valueOf(saved.getId()),
                            "Expense claim submitted for regular approval.");
                }
                case "LEAVE_REQUEST" -> {
                    LeaveRequest leave = new LeaveRequest();
                    leave.setStartDate(LocalDate.parse(f.get("startDate")));
                    leave.setEndDate(LocalDate.parse(f.get("endDate")));
                    leave.setLeaveType(f.get("leaveType"));
                    leave.setLeaveDurationType(f.get("leaveDurationType"));
                    leave.setReason(f.get("reason"));
                    LeaveRequest saved = portal.applyLeave(me.auth(), leave);
                    result = new Result(action.type(), "SUBMITTED", String.valueOf(saved.getId()), "Leave request submitted for regular approval.");
                }
                case "TIMESHEET_ENTRY" -> {
                    Map<String,Object> payload = new HashMap<>(f);
                    Map<String,Object> saved = portal.createTimesheet(me.auth(), payload);
                    result = new Result(action.type(), "SUBMITTED", String.valueOf(saved.get("id")), "Timesheet submitted for regular review.");
                }
                case "REVIEW_LEAVE" -> {
                    Long id = Long.valueOf(f.get("recordId"));
                    Map<String,String> body = Map.of("status", f.get("decision"), "approvalComment", f.get("comment"));
                    portal.updateLeave(me.auth(), id, body); // Reuses authorization, pending checks, balances and notifications.
                    result = new Result(action.type(), f.get("decision"), String.valueOf(id), "Leave decision recorded.");
                }
                case "REVIEW_TIMESHEET" -> {
                    Long id = Long.valueOf(f.get("recordId"));
                    Map<String,String> body = Map.of("status", f.get("decision"), "comment", f.get("comment"));
                    portal.updateTimesheet(me.auth(), id, body); // Reuses mentor and review workflow rules.
                    result = new Result(action.type(), f.get("decision"), String.valueOf(id), "Timesheet decision recorded.");
                }
                default -> throw bad("Unsupported action.");
            }
            // Record the receipt within the SAME database transaction as the workflow write.
            // A database commit makes the business record and its receipt visible atomically.
            // The token was already consumed in an independent transaction to prevent replay.
            tokenStore.recordCommit(token, me.username(), me.role(), result.reference());
            auditWhenCommitted("ACTION_CONFIRM_" + action.type());
            return result;
        } catch (RuntimeException ex) {
            // One-time consumption prevents duplicate writes after an ambiguous network/database error.
            audit.record("ACTION_CONFIRM_" + action.type(), "FAILED");
            throw ex;
        }
    }

    /** An action's receipt has already been written in the SAME business transaction.
     * Emit SUCCESS only after that transaction commits, never before it.
     * No separate after-commit receipt write may create a false unknown outcome. */
    private void auditWhenCommitted(String event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("AI action confirmation requires a managed business transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { audit.record(event, "SUCCESS"); }
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) audit.record(event, "FAILED");
            }
        });
    }

    private Identity verifiedIdentity() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()))
            throw new AccessDeniedException("Authentication required.");
        String username = auth.getName();
        Employee employee = employees.findByRegisterEmployee_UserId(username)
                .or(() -> employees.findByEmployeeProfile_UserId(username))
                .or(() -> employees.findByEmail(username)).orElse(null);
        if (employee == null || employee.getRegisterEmployee() == null)
            throw new AccessDeniedException("Employee identity could not be verified.");
        String role = employee.getRegisterEmployee().getRole();
        role = role == null ? "" : (role.toUpperCase(Locale.ROOT).startsWith("ROLE_") ? role.toUpperCase(Locale.ROOT) : "ROLE_" + role.toUpperCase(Locale.ROOT));
        final String assignedRole = role;
        if (auth.getAuthorities().size() != 1 || auth.getAuthorities().stream().noneMatch(a -> assignedRole.equalsIgnoreCase(a.getAuthority())))
            throw new AccessDeniedException("Your role changed. Sign out and sign in again.");
        return new Identity(username, role, employee, auth);
    }

    /** Field allowlist prevents binding IDs, owners, reviewers, status, or embedded entities. */
    static Map<String,String> validate(String type, Map<String,String> original) {
        if (original == null) throw bad("Action fields are required.");
        Set<String> allowed = switch(type) {
            case "SUPPORT_TICKET", "HR_SERVICE_REQUEST" -> Set.of("subject", "description", "category", "priority");
            case "OVERTIME_REQUEST" -> Set.of("workDate", "hours", "project", "reason", "compensationMode");
            case "EXPENSE_CLAIM" -> Set.of("category", "amount", "expenseDate", "description", "receiptName");
            case "LEAVE_REQUEST" -> Set.of("startDate", "endDate", "leaveType", "leaveDurationType", "reason");
            case "TIMESHEET_ENTRY" -> Set.of("date", "hoursWorked", "taskDescription", "workMode");
            case "REVIEW_LEAVE", "REVIEW_TIMESHEET" -> Set.of("recordId", "decision", "comment");
            default -> throw bad("Unsupported action.");
        };
        if (!allowed.containsAll(original.keySet())) throw bad("Unexpected fields in the action draft.");
        Map<String,String> f = new LinkedHashMap<>();
        for (String key:allowed) f.put(key, original.get(key) == null ? "" : original.get(key).trim());
        switch(type) {
            case "SUPPORT_TICKET", "HR_SERVICE_REQUEST" -> {
                length(f.get("subject"), 5, 160, "Subject");
                length(f.get("description"), 10, 2000, "Description");
                f.put("category", def(f.get("category"), "HR_SERVICE_REQUEST".equals(type) ? "OTHER" : "IT SUPPORT").toUpperCase(Locale.ROOT));
                f.put("priority", def(f.get("priority"), "MEDIUM").toUpperCase(Locale.ROOT));
                if (("SUPPORT_TICKET".equals(type) && !TICKET_CATEGORIES.contains(f.get("category")))
                        || ("HR_SERVICE_REQUEST".equals(type) && !Set.of("PAYROLL", "BENEFITS", "DOCUMENTS", "ATTENDANCE", "OTHER").contains(f.get("category")))
                        || !PRIORITIES.contains(f.get("priority")))
                    throw bad("Invalid request category or priority.");
            }
            case "OVERTIME_REQUEST" -> {
                LocalDate workDate = date(f.get("workDate"), "Overtime work date");
                if (workDate.isAfter(LocalDate.now()) || workDate.isBefore(LocalDate.now().minusDays(62)))
                    throw bad("Overtime date must be within the past 63 days and not in the future.");
                if (!f.get("hours").matches("[0-9]{1,2}(\\.[0-9]{1,2})?"))
                    throw bad("Enter valid overtime hours.");
                double hours = Double.parseDouble(f.get("hours"));
                if (hours <= 0 || hours > 24) throw bad("Overtime hours must be from 0.01 to 24.");
                length(f.get("reason"), 8, 1200, "Overtime reason");
                if (f.get("project").length() > 100) throw bad("Project is too long.");
                f.put("compensationMode", def(f.get("compensationMode"), "PAYMENT").toUpperCase(Locale.ROOT));
                if (!Set.of("PAYMENT", "COMP_OFF").contains(f.get("compensationMode")))
                    throw bad("Choose a supported compensation mode.");
            }
            case "EXPENSE_CLAIM" -> {
                if (!EXPENSE_CATEGORIES.contains(f.get("category"))) throw bad("Choose a supported expense category.");
                try {
                    // Prevent exponent, signs, NaN, infinity and silently rounded fractional paise.
                    if (!f.get("amount").matches("[0-9]{1,7}(\\.[0-9]{1,2})?"))
                        throw new NumberFormatException();
                    java.math.BigDecimal amount = new java.math.BigDecimal(f.get("amount"));
                    if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0 ||
                            amount.compareTo(new java.math.BigDecimal("1000000")) > 0)
                        throw new NumberFormatException();
                    f.put("amount", amount.stripTrailingZeros().toPlainString());
                } catch (NumberFormatException e) { throw bad("Enter an expense amount from 0.01 to 1000000 with at most two decimal places."); }
                LocalDate expenseDate = date(f.get("expenseDate"), "Expense date");
                if (expenseDate.isAfter(LocalDate.now()) || expenseDate.isBefore(LocalDate.now().minusDays(366)))
                    throw bad("Expense date must be within the last 367 days and not in the future.");
                length(f.get("description"), 10, 1000, "Expense description");
                if (f.get("receiptName").length() > 160) throw bad("Receipt reference must contain at most 160 characters.");
            }
            case "LEAVE_REQUEST" -> {
                LocalDate start = date(f.get("startDate"), "Start date");
                LocalDate end = date(f.get("endDate"), "End date");
                if (start.isBefore(LocalDate.now()) || end.isBefore(start) || end.isAfter(start.plusDays(90)))
                    throw bad("Choose a non-past leave period of at most 91 days.");
                length(f.get("reason"), 3, 900, "Reason");
                f.put("leaveDurationType", def(f.get("leaveDurationType"), "FULL_DAY").toUpperCase(Locale.ROOT));
                if (!DURATIONS.contains(f.get("leaveDurationType"))) throw bad("Choose a valid leave duration.");
                if (!"FULL_DAY".equals(f.get("leaveDurationType")) && !start.equals(end))
                    throw bad("Half-day requests must have matching dates.");
                if ("FULL_DAY".equals(f.get("leaveDurationType"))) length(f.get("leaveType"), 2, 100, "Leave category");
                else f.put("leaveType", "Short Leave");
            }
            case "REVIEW_LEAVE", "REVIEW_TIMESHEET" -> {
                try {
                    long id = Long.parseLong(f.get("recordId"));
                    if (id <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) { throw bad("Select a valid pending request."); }
                f.put("decision", f.get("decision").toUpperCase(Locale.ROOT));
                if (!Set.of("APPROVED", "REJECTED").contains(f.get("decision")))
                    throw bad("Choose Approve or Reject.");
                length(f.get("comment"), 3, 1000, "Review rationale");
            }
            case "TIMESHEET_ENTRY" -> {
                LocalDate date = date(f.get("date"), "Timesheet date");
                if (date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.now().minusDays(62)))
                    throw bad("Timesheet date must be within the last 63 days and not in the future.");
                try {
                    double h = Double.parseDouble(f.get("hoursWorked"));
                    if (!Double.isFinite(h) || h <= 0 || h > 24) throw new NumberFormatException();
                } catch (NumberFormatException e) { throw bad("Hours worked must be more than 0 and at most 24."); }
                length(f.get("taskDescription"), 3, 900, "Task description");
                f.put("workMode", def(f.get("workMode"), "OFFICE").toUpperCase(Locale.ROOT));
                if (!Set.of("OFFICE", "HYBRID", "WFH").contains(f.get("workMode")))
                    throw bad("Choose a supported work mode.");
            }
        }
        return Map.copyOf(f);
    }
    private static String summary(String type, Map<String,String> f) {
        return switch (type) {
            case "SUPPORT_TICKET" -> "Create support ticket: " + f.get("subject") + " [" + f.get("priority") + "]";
            case "HR_SERVICE_REQUEST" -> "Submit HR service request: " + f.get("subject") + " [" + f.get("category") + "]";
            case "OVERTIME_REQUEST" -> "Submit " + f.get("hours") + " overtime hours on " + f.get("workDate") + " for regular approval";
            case "EXPENSE_CLAIM" -> "Submit expense claim: INR " + f.get("amount") + " for " + f.get("category") + " dated " + f.get("expenseDate");
            case "LEAVE_REQUEST" -> "Submit " + f.get("leaveDurationType") + " leave (" + f.get("leaveType") + ") from " + f.get("startDate") + " to " + f.get("endDate");
            case "TIMESHEET_ENTRY" -> "Submit " + f.get("hoursWorked") + " hours on " + f.get("date") + " (" + f.get("workMode") + ")";
            default -> "Unsupported";
        };
    }
    private static String def(String s, String fallback) { return s == null || s.isBlank() ? fallback : s; }
    private static LocalDate date(String value, String label) { try { return LocalDate.parse(value); } catch (Exception e) { throw bad(label + " must use YYYY-MM-DD."); } }
    private static void length(String value, int min, int max, String field) {
        if (value == null || value.length() < min || value.length() > max) throw bad(field + " must contain " + min + " to " + max + " characters.");
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
