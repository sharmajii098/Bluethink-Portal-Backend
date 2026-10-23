package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.dto.LeaveBalanceAdjustDTO;
import EmployeeManagementSystem.dto.LeaveRequestDTO;
import EmployeeManagementSystem.dto.SalaryStructureDTO;
import EmployeeManagementSystem.dto.SalaryStructureResponseDTO;
import EmployeeManagementSystem.dto.EmployeeReportDTO;
import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.entity.LeaveBalance;
import EmployeeManagementSystem.entity.LeavePolicy;
import EmployeeManagementSystem.entity.LeaveType;
import EmployeeManagementSystem.entity.SalaryStructure;
import EmployeeManagementSystem.enums.AttendanceStatus;
import EmployeeManagementSystem.enums.LeaveStatus;
import EmployeeManagementSystem.enums.ProjectStatus;
import EmployeeManagementSystem.enums.WorkMode;
import EmployeeManagementSystem.repository.*;
import EmployeeManagementSystem.repository.LeaveBalanceRepository;
import EmployeeManagementSystem.repository.LeaveTypeRepository;
import EmployeeManagementSystem.service.DashboardService;
import EmployeeManagementSystem.service.CredentialSmsService;
import EmployeeManagementSystem.service.LeaveWorkflowRules;
import EmployeeManagementSystem.service.WorkflowApprovalPolicy;
import EmployeeManagementSystem.service.EmployeeReportService;
import EmployeeManagementSystem.service.EmployeeHierarchyService;
import EmployeeManagementSystem.service.AdminLeaveService;
import EmployeeManagementSystem.service.MonthlySalaryService;
import EmployeeManagementSystem.service.SalaryStructureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.security.SecureRandom;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalTime;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class FrontendApiController {
    // Configure shift start and grace using ATTENDANCE_OFFICE_START and ATTENDANCE_GRACE_MINUTES.
    private final EmployeeProfileRepository profileRepo;
    private final RegisterEmployeeRepository registerRepo;
    private final EmployeeRepository employeeRepo;
    private final DepartmentRepository departmentRepo;
    private final AttendanceRepository attendanceRepo;
    private final ShiftAssignmentRepository shiftAssignmentRepo;
    @Value("${attendance.office-start:10:00}")
    private String officeStart;
    @Value("${attendance.grace-minutes:15}")
    private int attendanceGraceMinutes;
    @Value("${attendance.timezone:${bluethink.business-zone:Asia/Kolkata}}")
    private String attendanceTimezone;
    private final LeaveRepository leaveRepo;
    private final TimesheetRepository timesheetRepo;
    private final ProjectRepository projectRepo;
    private final SalaryRepository salaryRepo;
    private final NotificationRepository notificationRepo;
    private final UserSessionRepository userSessionRepo;
    private final DashboardService dashboardService;
    private final EmployeeReportService employeeReportService;
    private final EmployeeHierarchyService employeeHierarchyService;
    private final AdminLeaveService adminLeaveService;
    private final MonthlySalaryService monthlySalaryService;
    private final SalaryStructureService salaryStructureService;
    private final LeaveBalanceRepository leaveBalanceRepo;
    private final LeaveTypeRepository leaveTypeRepo;
    private final HolidayRepository holidayRepo;
    private final WfhRequestRepository wfhRepo;
    private final MentorshipAssignmentRepository mentorshipRepo;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final CredentialSmsService credentialSmsService;
    private final EmployeeManagementSystem.service.AccountInvitationService invitationService;
    private final EmployeeManagementSystem.service.NotificationSseService notificationSseService;

    public FrontendApiController(EmployeeProfileRepository profileRepo, RegisterEmployeeRepository registerRepo,
                                 EmployeeRepository employeeRepo, DepartmentRepository departmentRepo,
                                 AttendanceRepository attendanceRepo, ShiftAssignmentRepository shiftAssignmentRepo,
                                 LeaveRepository leaveRepo,
                                 TimesheetRepository timesheetRepo, ProjectRepository projectRepo,
                                 SalaryRepository salaryRepo, NotificationRepository notificationRepo, UserSessionRepository userSessionRepo,
                                 DashboardService dashboardService, EmployeeReportService employeeReportService,
                                 EmployeeHierarchyService employeeHierarchyService, AdminLeaveService adminLeaveService,
                                 MonthlySalaryService monthlySalaryService, SalaryStructureService salaryStructureService,
                                 LeaveBalanceRepository leaveBalanceRepo, LeaveTypeRepository leaveTypeRepo,
                                 HolidayRepository holidayRepo, WfhRequestRepository wfhRepo, MentorshipAssignmentRepository mentorshipRepo, ObjectMapper objectMapper, PasswordEncoder passwordEncoder,
                                 JavaMailSender mailSender, CredentialSmsService credentialSmsService, EmployeeManagementSystem.service.AccountInvitationService invitationService, EmployeeManagementSystem.service.NotificationSseService notificationSseService) {
        this.profileRepo = profileRepo; this.registerRepo = registerRepo; this.employeeRepo = employeeRepo;
        this.departmentRepo = departmentRepo; this.attendanceRepo = attendanceRepo;
        this.shiftAssignmentRepo = shiftAssignmentRepo; this.leaveRepo = leaveRepo;
        this.timesheetRepo = timesheetRepo; this.projectRepo = projectRepo; this.salaryRepo = salaryRepo;
        this.notificationRepo = notificationRepo;
        this.userSessionRepo = userSessionRepo;
        this.dashboardService = dashboardService;
        this.employeeReportService = employeeReportService; this.employeeHierarchyService = employeeHierarchyService;
        this.adminLeaveService = adminLeaveService; this.monthlySalaryService = monthlySalaryService;
        this.salaryStructureService = salaryStructureService; this.leaveBalanceRepo = leaveBalanceRepo;
        this.leaveTypeRepo = leaveTypeRepo; this.holidayRepo = holidayRepo; this.wfhRepo = wfhRepo; this.mentorshipRepo = mentorshipRepo; this.objectMapper = objectMapper; this.passwordEncoder = passwordEncoder; this.mailSender = mailSender; this.credentialSmsService = credentialSmsService; this.invitationService = invitationService; this.notificationSseService = notificationSseService;
    }

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard/stats")
    public Map<String, Object> dashboard(Authentication auth) {
        String currentRole = role(auth);
        if (!"ADMIN".equals(currentRole) && !"HR".equals(currentRole)) {
            return Map.of("role", currentRole);
        }

        var dto = dashboardService.getDashboardStats();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalEmployees", dto.getTotalEmployees());
        out.put("departments", dto.getDepartments());
        out.put("attendanceRate", dto.getAttendanceRate());
        out.put("totalPayrollCost", dto.getTotalPayrollCost());
        out.put("paidSalary", dto.getPaidSalary());
        out.put("pendingSalary", dto.getPendingSalary());
        out.put("paidEmployees", dto.getPaidEmployees());
        out.put("pendingEmployees", dto.getPendingEmployees());
        out.put("averageSalary", dto.getAverageSalary());
        out.put("highestSalary", dto.getHighestSalary());
        out.put("lowestSalary", dto.getLowestSalary());
        out.put("joined", dto.getJoined());
        out.put("left", dto.getLeft());
        out.put("attendanceTrend", dto.getAttendanceTrend());
        out.put("departmentNames", dto.getDepartmentNames());
        out.put("departmentPayroll", dto.getDepartmentPayroll());
        out.put("payrollMonths", dto.getPayrollMonths());
        out.put("monthlyPayroll", dto.getMonthlyPayroll());
        out.put("paymentStatusLabels", dto.getPaymentStatusLabels());
        out.put("paymentStatusCounts", dto.getPaymentStatusCounts());
        out.put("role", currentRole);
        return out;
    }

    // ==================== EMPLOYEES ====================

    @GetMapping("/employees")
    public List<Map<String, Object>> employees() {
        return profileRepo.findAll().stream().map(this::profileMap).toList();
    }

    @GetMapping("/employees/{id}")
    public Map<String, Object> employee(@PathVariable Long id) {
        return profileMap(profileRepo.findById(id).orElseThrow(() -> notFound("Employee")));
    }

    @GetMapping("/employees/team")
    public List<Map<String, Object>> teamEmployees(Authentication auth) {
        Employee manager = currentEmployee(auth);
        return employeeRepo.findByReportingManager_Id(manager.getId()).stream().map(this::employeeMap).toList();
    }

    @GetMapping("/employees/reporting-managers")
    public List<Map<String, Object>> reportingManagers() {
        return employeeRepo.findAll().stream()
                .filter(e -> e.getRegisterEmployee() != null)
                .filter(e -> "MANAGER".equals(normalizeRole(e.getRegisterEmployee().getRole())))
                .map(this::employeeMap)
                .toList();
    }

    /**
     * Creates the complete account graph required by the application:
     * RegisterEmployee -> Employee -> EmployeeProfile.
     */
    @PostMapping("/employees")
    @Transactional
    public Map<String, Object> createEmployee(
            Authentication auth,
            @RequestBody Map<String, Object> body) {

        // ============================================================
        // 1. CHECK CREATOR ROLE
        // ============================================================

        String creatorRole = role(auth);

        if (!"ADMIN".equals(creatorRole) && !"HR".equals(creatorRole)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only Admin or HR can create users."
            );
        }

        // ============================================================
        // 2. READ REQUEST DATA
        // ============================================================

        String fullName = text(body.get("fullName"));
        String email = text(body.get("email")).toLowerCase(Locale.ENGLISH);
        String phone = text(body.get("phoneNumber"));

        // Support both phoneNumber and phone
        if (phone.isBlank()) {
            phone = text(body.get("phone"));
        }

        String requestedRole =
                normalizeRole(text(body.get("role")));

        String departmentName =
                text(body.get("department"));

        String designation =
                text(body.get("designation"));

        String userId = nextUserId();

        String providedPassword = generateTemporaryPassword(16);

        // ============================================================
        // 3. VALIDATE REQUIRED FIELDS
        // ============================================================

        if (fullName.isBlank()) {
            bad("Full name is required");
        }

        if (!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            bad("Enter a valid work email address");
        }
        if (!phone.isBlank() && !phone.matches("[0-9]{10}")) {
            bad("Mobile number must be exactly 10 digits");
        }
        if (designation.isBlank()) bad("Select a designation");

        if (userId.isBlank()) {
            bad("User ID is required");
        }

        if (providedPassword.isBlank()) {
            bad("Password is required");
        }

        if (requestedRole.isBlank()) {
            bad("Role is required");
        }

        LocalDate dob =
                parseDate(
                        body.get("dob"),
                        "Date of birth"
                );

        // ============================================================
        // 4. ROLE PERMISSION
        // ============================================================

        Set<String> allowedRoles;

        if ("ADMIN".equals(creatorRole)) {

            // ADMIN can create:
            // HR
            // MANAGER
            // EMPLOYEE

            allowedRoles = Set.of(
                    "HR",
                    "MANAGER",
                    "EMPLOYEE"
            );

        } else {

            // HR can create:
            // MANAGER
            // EMPLOYEE

            allowedRoles = Set.of(
                    "MANAGER",
                    "EMPLOYEE"
            );
        }

        if (!allowedRoles.contains(requestedRole)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot create the selected role."
            );
        }

        // ============================================================
        // 5. CHECK DUPLICATE EMAIL
        // ============================================================

        if (profileRepo.findByEmail(email).isPresent()
                || registerRepo.findByEmail(email).isPresent()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already exists."
            );
        }

        // ============================================================
        // 6. VERIFY AUTO-GENERATED USER ID
        // ============================================================

        if (registerRepo.findByUserId(userId) != null || profileRepo.existsByUserId(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User ID already exists."
            );
        }

        // ============================================================
        // 7. RESOLVE DEPARTMENT
        // ============================================================

        Department department = null;

        if (!departmentName.isBlank()) {

            department = departmentRepo
                    .findByDepartmentName(departmentName)
                    .orElseThrow(() ->
                            badException(
                                    "Department not found: "
                                            + departmentName
                            )
                    );
        }

        // ============================================================
        // 8. RESOLVE REPORTING MANAGER
        // ============================================================

        Employee reportingManager = null;

        Long reportingManagerId =
                longValue(body.get("reportingManagerId"));

        if (reportingManagerId != null) {

            reportingManager = employeeRepo
                    .findById(reportingManagerId)
                    .orElseThrow(() ->
                            badException(
                                    "Reporting manager not found."
                            )
                    );

            RegisterEmployee managerAccount = reportingManager.getRegisterEmployee();
            if (managerAccount == null || !"MANAGER".equals(normalizeRole(managerAccount.getRole()))) {
                throw badException("Reporting manager must have the MANAGER role.");
            }
            if (department == null && reportingManager.getDepartment() != null) {
                department = reportingManager.getDepartment();
            }
            if (department != null && (reportingManager.getDepartment() == null
                    || !Objects.equals(department.getId(), reportingManager.getDepartment().getId()))) {
                throw badException("Employee and reporting manager must belong to the same department.");
            }
        }

        // ============================================================
        // 9. CREATE REGISTER EMPLOYEE ACCOUNT
        // ============================================================

        RegisterEmployee account =
                new RegisterEmployee();

        account.setUserId(userId);
        account.setName(fullName);
        account.setEmail(email);

        account.setPhoneNumber(
                phone.isBlank()
                        ? null
                        : phone
        );

        // Store encoded password in database.
        account.setPassword(
                passwordEncoder.encode(
                        providedPassword
                )
        );

        account.setRole(requestedRole);

        account.setDesignation(
                designation.isBlank()
                        ? null
                        : designation
        );

        if (department != null) {
            account.setDepartmentId(
                    department.getId()
            );
        }

        if (reportingManager != null) {
            account.setReportingManagerId(
                    reportingManager.getId()
            );
        }

        account =
                registerRepo.save(account);

        // ============================================================
        // 10. CREATE EMPLOYEE
        // ============================================================

        Employee employee =
                new Employee();

        employee.setFullName(fullName);

        String[] nameParts =
                splitName(fullName);

        employee.setFirstName(nameParts[0]);
        employee.setLastName(nameParts[1]);

        employee.setEmail(email);

        employee.setPhone(
                phone.isBlank()
                        ? null
                        : phone
        );

        employee.setDateOfBirth(dob);

        employee.setJoiningDate(
                parseOptionalDate(
                        body.get("joiningDate")
                )
        );

        employee.setWorkMode(
                textOrNull(
                        body.get("workMode")
                )
        );

        employee.setDepartment(
                department
        );

        employee.setReportingManager(
                reportingManager
        );

        employee.setRegisterEmployee(
                account
        );

        employee =
                employeeRepo.save(employee);

        // ============================================================
        // 11. CREATE EMPLOYEE PROFILE
        // ============================================================

        EmployeeProfile profile =
                new EmployeeProfile();

        profile.setUserId(userId);

        profile.setEmployee(employee);

        profile.setFullName(fullName);

        profile.setEmail(email);

        profile.setPhoneNumber(
                phone.isBlank()
                        ? null
                        : phone
        );

        profile.setDob(dob);

        profile.setGender(upperTextOrNull(body.get("gender")));
        profile.setBloodGroup(upperTextOrNull(body.get("bloodGroup")));
        profile.setMaritalStatus(upperTextOrNull(body.get("maritalStatus")));

        profile.setDepartment(
                department == null
                        ? null
                        : department.getDepartmentName()
        );

        profile.setDesignation(
                designation.isBlank()
                        ? null
                        : designation
        );

        profile.setEmployeeBand(upperTextOrNull(body.get("employeeBand")));

        profile.setPanNumber(
                textOrNull(body.get("panNumber"))
        );

        profile.setCurrentAddress(
                textOrNull(
                        body.get("currentAddress")
                )
        );

        profile.setPermanentAddress(
                textOrNull(
                        body.get("permanentAddress")
                )
        );

        String status =
                text(body.get("status"));

        profile.setStatus(
                status.isBlank()
                        ? "ACTIVE"
                        : status.toUpperCase()
        );

        profile.setRegisteredAt(
                LocalDateTime.now()
        );

        profile =
                profileRepo.saveAndFlush(profile);

        // ============================================================
        // 12. COMPLETE RELATIONSHIPS
        // ============================================================

        account.setEmployee(employee);
        employee.setEmployeeProfile(profile);
        registerRepo.saveAndFlush(account);
        // Catch database constraint errors before triggering any external credential delivery.
        // Transaction commit still occurs after this method returns.

        // Send only a one-time activation URL after the whole account transaction commits.
        // The random initial password is not disclosed and is replaced at activation.
        String invitationToken = invitationService.issue(account);
        // account was reassigned after save; capture a final reference for the afterCommit callback.
        final RegisterEmployee invitedAccount = account;
        Map<String, Object> result = new LinkedHashMap<>(profileMap(profile));
        result.put("userId", userId);
        result.put("deliveryStatus", "PENDING");
        result.put("credentialsEmailSent", false); // compatibility with older clients
        result.put("credentialsSmsSent", false);
        result.put("smsStatus", "NOT_CONFIGURED");
        result.put("message", "Employee created. Invitation will be sent after account commit.");
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() {
                    boolean accepted = invitationService.send(invitedAccount, invitationToken);
                    result.put("deliveryStatus", accepted ? "SMTP_ACCEPTED" : "FAILED");
                    result.put("credentialsEmailSent", accepted);
                    result.put("message", accepted
                        ? "Employee created. Gmail accepted the setup invitation; check the employee inbox."
                        : "Employee created, but Gmail could not send the invitation. Use Resend Invitation after fixing SMTP.");
                }
            });
        return result;
    }


    @PutMapping("/employees/{id}")
    @Transactional
    public Map<String, Object> updateEmployee(Authentication auth, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        EmployeeProfile profile = profileRepo.findById(id).orElseThrow(() -> notFound("Employee"));
        Employee employee = profile.getEmployee();
        RegisterEmployee account = employee != null ? employee.getRegisterEmployee() : registerRepo.findByUserId(profile.getUserId());
        if ("HR".equals(role(auth)) && account != null &&
                Set.of("ADMIN", "HR").contains(normalizeRole(account.getRole()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Admin can modify HR/Admin accounts");
        }
        if ("HR".equals(role(auth)) && body.containsKey("role") &&
                Set.of("ADMIN", "HR").contains(normalizeRole(text(body.get("role"))))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HR cannot grant HR or Admin permissions");
        }

        String fullName = text(body.get("fullName"));
        String email = text(body.get("email")).toLowerCase(Locale.ENGLISH);
        if (!email.isBlank() && !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
            bad("Enter a valid work email address");
        // updateProfileFields uses the request map, so keep its email canonical too.
        if (!email.isBlank()) body.put("email", email);
        if (!fullName.isBlank()) profile.setFullName(fullName);
        if (!email.isBlank() && !email.equalsIgnoreCase(profile.getEmail())) {
            if (profileRepo.findByEmail(email).filter(p -> !p.getId().equals(id)).isPresent() ||
                    registerRepo.findByEmail(email).filter(r -> account == null || !r.getId().equals(account.getId())).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
            }
            profile.setEmail(email);
        }
        updateProfileFields(profile, body);
        if (body.containsKey("designation") && text(body.get("designation")).isBlank())
            bad("Please select a designation");
        // A title or band is an employment attribute and must not implicitly change login permissions.

        if (account != null) {
            account.setName(profile.getFullName());
            account.setEmail(profile.getEmail());
            account.setPhoneNumber(profile.getPhoneNumber());
            account.setDesignation(profile.getDesignation());
            if (!text(body.get("role")).isBlank()) account.setRole(normalizeRole(text(body.get("role"))));
            String newPassword = text(body.get("password"));
            if (!newPassword.isBlank()) account.setPassword(passwordEncoder.encode(newPassword));
            Department d = resolveDepartment(text(body.get("department")));
            if (d != null) {
                account.setDepartmentId(d.getId());
                if (employee != null) employee.setDepartment(d);
                profile.setDepartment(d.getDepartmentName());
            }
            if (employee != null) {
                employee.setFullName(profile.getFullName());
                employee.setEmail(profile.getEmail());
                employee.setPhone(profile.getPhoneNumber());
                employee.setDateOfBirth(profile.getDob());
                if (body.containsKey("joiningDate")) employee.setJoiningDate(parseOptionalDate(body.get("joiningDate")));
                if (body.containsKey("workMode")) employee.setWorkMode(textOrNull(body.get("workMode")));
                if (body.containsKey("reportingManagerId")) {
                    Long managerId = longValue(body.get("reportingManagerId"));
                    Employee manager = managerId == null ? null : employeeRepo.findById(managerId).orElseThrow(() -> badException("Reporting manager not found"));
                    if (manager != null && manager.getId().equals(employee.getId())) throw badException("Employee cannot report to themselves");
                    if (manager != null) {
                        RegisterEmployee managerAccount = manager.getRegisterEmployee();
                        if (managerAccount == null || !"MANAGER".equals(normalizeRole(managerAccount.getRole()))) {
                            throw badException("Reporting manager must have the MANAGER role");
                        }
                        if (employee.getDepartment() == null && manager.getDepartment() != null) {
                            employee.setDepartment(manager.getDepartment());
                            account.setDepartmentId(manager.getDepartment().getId());
                            profile.setDepartment(manager.getDepartment().getDepartmentName());
                        }
                        if (employee.getDepartment() != null && (manager.getDepartment() == null
                                || !Objects.equals(employee.getDepartment().getId(), manager.getDepartment().getId()))) {
                            throw badException("Employee and reporting manager must belong to the same department");
                        }
                    }
                    employee.setReportingManager(manager);
                    account.setReportingManagerId(manager == null ? null : manager.getId());
                }
                employeeRepo.save(employee);
            }
            registerRepo.save(account);
        }
        return profileMap(profileRepo.save(profile));
    }

    @PatchMapping("/employees/{id}/status")
    @Transactional
    public Map<String, Object> updateEmployeeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        EmployeeProfile profile = profileRepo.findById(id).orElseThrow(() -> notFound("Employee"));
        String status = text(body.get("status")).trim().toUpperCase();
        if (!List.of("ACTIVE", "INACTIVE").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE or INACTIVE");
        }
        profile.setStatus(status);
        profile.setUpdatedAt(LocalDateTime.now());
        profileRepo.save(profile);
        RegisterEmployee account = profile.getEmployee() == null ? null : profile.getEmployee().getRegisterEmployee();
        if ("INACTIVE".equals(status) && account != null) {
            userSessionRepo.deactivateEmployeeSessions(account.getUserId());
        }
        return profileMap(profile);
    }

    @DeleteMapping("/employees/{id}")
    @Transactional
    public Map<String, Object> deleteEmployee(@PathVariable Long id) {
        EmployeeProfile profile = profileRepo.findById(id).orElseThrow(() -> notFound("Employee"));
        Employee employee = profile.getEmployee();
        RegisterEmployee account = employee != null ? employee.getRegisterEmployee() : registerRepo.findByUserId(profile.getUserId());

        // Soft-delete instead of physically removing the employee graph.
        // Attendance, salary, leave and project records may reference it.
        profile.setStatus("INACTIVE");
        profile.setUpdatedAt(LocalDateTime.now());
        profileRepo.save(profile);

        if (account != null) {
            userSessionRepo.deactivateEmployeeSessions(account.getUserId());
        }

        return Map.of("success", true, "message", "Employee deactivated successfully.");
    }

    // ==================== DEPARTMENTS ====================

    @GetMapping("/departments")
    public List<Department> departments() { return departmentRepo.findAll(); }

    @PostMapping("/departments")
    @Transactional
    public Department createDepartment(@RequestBody Department d) {
        d.setId(null);
        return departmentRepo.save(d);
    }

    @PutMapping("/departments/{id}")
    public Department updateDepartment(@PathVariable Long id, @RequestBody Department incoming) {
        Department d = departmentRepo.findById(id).orElseThrow(() -> notFound("Department"));
        d.setDepartmentName(incoming.getDepartmentName());
        d.setDescription(incoming.getDescription());
        d.setStatus(incoming.getStatus());
        d.setDesignations(incoming.getDesignations());
        return departmentRepo.save(d);
    }

    @DeleteMapping("/departments/{id}")
    @Transactional
    public Map<String, Object> deleteDepartment(@PathVariable Long id) {
        Department d = departmentRepo.findById(id).orElseThrow(() -> notFound("Department"));
        String name = d.getDepartmentName();
        boolean assigned = employeeRepo.findAll().stream()
                .anyMatch(e -> e.getDepartment() != null && id.equals(e.getDepartment().getId()));
        if (assigned) {
            throw badException("Department is assigned to employees. Deactivate it instead of deleting it.");
        }
        d.setStatus("INACTIVE");
        departmentRepo.save(d);
        return Map.of("success", true, "message", "Department archived successfully.", "department", name);
    }

    // ==================== ATTENDANCE ====================

    @GetMapping("/attendance/context")
    public Map<String, String> attendanceContext() {
        ZoneId zone = ZoneId.of(attendanceTimezone);
        return Map.of("today", LocalDate.now(zone).toString(), "timezone", zone.getId());
    }

    @GetMapping("/attendance")
    public List<Map<String, Object>> allAttendance() {
        return attendanceRepo.findAll().stream()
                .sorted(Comparator.comparing(Attendance::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::attendanceMap).toList();
    }

    @GetMapping("/attendance/my")
    public List<Map<String, Object>> myAttendance(Authentication auth) {
        return findCurrentEmployee(auth)
                .map(e -> { expireForgottenAttendance(e);
                    return attendanceRepo.findByEmployee(e).stream()
                        .sorted(Comparator.comparing(Attendance::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(this::attendanceMap).toList(); })
                .orElse(List.of());
    }

    @GetMapping("/attendance/team")
    public List<Map<String, Object>> teamAttendance(Authentication auth) {
        Employee manager = currentEmployee(auth);
        Set<Long> ids = employeeRepo.findByReportingManager_Id(manager.getId()).stream().map(Employee::getId).collect(Collectors.toSet());
        return attendanceRepo.findAll().stream().filter(a -> a.getEmployee() != null && ids.contains(a.getEmployee().getId()))
                .sorted(Comparator.comparing(Attendance::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::attendanceMap).toList();
    }

    @GetMapping("/attendance/summary")
    public Map<String, Object> attendanceSummary(Authentication auth,
                                                   @RequestParam(required = false) String date,
                                                   @RequestParam(required = false) String department) {
        String r = role(auth);
        if (!("ADMIN".equals(r) || "HR".equals(r) || "MANAGER".equals(r)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attendance summary is restricted to management roles");
        LocalDate target = date == null || date.isBlank() ? LocalDate.now() : parseDate(date, "Attendance date");
        String dept = textOrNull(department);
        long present = attendanceRepo.countByDateAndStatusAndDepartment(target, AttendanceStatus.PRESENT, dept);
        long late = attendanceRepo.countByDateAndStatusAndDepartment(target, AttendanceStatus.LATE, dept);
        long absent = attendanceRepo.countByDateAndStatusAndDepartment(target, AttendanceStatus.ABSENT, dept);
        long leave = attendanceRepo.countByDateAndStatusAndDepartment(target, AttendanceStatus.LEAVE, dept);
        long totalEmployees = attendanceRepo.countTotalEmployeesByDepartment(dept);
        long recorded = attendanceRepo.findByDate(target).stream().filter(a -> a.getEmployee() != null)
                .filter(a -> dept == null || (a.getEmployee().getDepartment() != null && dept.equals(a.getEmployee().getDepartment().getDepartmentName())))
                .count();
        // A missing punch is an unverified record, not conclusive absence.
        long unrecorded = Math.max(0, totalEmployees - recorded);
        return Map.of("date", target, "present", present, "late", late, "absent", absent, "unrecorded", unrecorded, "leave", leave, "totalEmployees", totalEmployees, "recorded", recorded);
    }

    @GetMapping("/attendance/calendar")
    public List<Map<String, Object>> attendanceCalendar(Authentication auth,
                                                         @RequestParam int year,
                                                         @RequestParam int month,
                                                         @RequestParam(required = false) Long employeeId) {
        String r = role(auth);
        List<Attendance> source;
        boolean aggregate = employeeId == null && ("ADMIN".equals(r) || "HR".equals(r) || "MANAGER".equals(r));
        if (employeeId != null && ("ADMIN".equals(r) || "HR".equals(r))) {
            Employee employee = employeeRepo.findById(employeeId).orElseThrow(() -> notFound("Employee"));
            source = attendanceRepo.findByEmployee(employee);
        } else if ("MANAGER".equals(r)) {
            Employee manager = currentEmployee(auth);
            Set<Long> ids = employeeRepo.findByReportingManager_Id(manager.getId()).stream().map(Employee::getId).collect(Collectors.toSet());
            source = attendanceRepo.findAll().stream().filter(a -> a.getEmployee() != null && ids.contains(a.getEmployee().getId())).toList();
        } else {
            Employee employee = currentEmployee(auth);
            source = attendanceRepo.findByEmployee(employee);
        }
        java.time.YearMonth ym = java.time.YearMonth.of(year, month);
        List<Attendance> inMonth = source.stream().filter(a -> a.getDate() != null && !a.getDate().isBefore(ym.atDay(1)) && !a.getDate().isAfter(ym.atEndOfMonth())).toList();
        if (aggregate) {
            return inMonth.stream().filter(a -> a.getDate() != null).collect(Collectors.groupingBy(Attendance::getDate, LinkedHashMap::new, Collectors.toList())).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> {
                        List<Attendance> day = entry.getValue();
                        long late = day.stream().filter(a -> a.getStatus() == AttendanceStatus.LATE).count();
                        long present = day.stream().filter(a -> a.getStatus() == AttendanceStatus.PRESENT).count();
                        long leave = day.stream().filter(a -> a.getStatus() == AttendanceStatus.LEAVE).count();
                        Map<String,Object> m = new LinkedHashMap<>();
                        m.put("date", entry.getKey()); m.put("status", late > 0 ? AttendanceStatus.LATE : present > 0 ? AttendanceStatus.PRESENT : leave > 0 ? AttendanceStatus.LEAVE : AttendanceStatus.ABSENT);
                        m.put("employeeCount", day.size());
                        m.put("workingHours", day.stream().map(Attendance::getWorkingHours).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum());
                        return m;
                    }).toList();
        }
        return inMonth.stream().sorted(Comparator.comparing(Attendance::getDate)).map(this::attendanceMap).toList();
    }

    @PostMapping("/attendance/punch-in")
    @Transactional
    public Map<String, Object> punchIn(Authentication auth, @RequestBody(required = false) Map<String, Object> body) {
        Employee e = currentEmployee(auth);
        expireForgottenAttendance(e);
        LocalDate today = LocalDate.now();
        Attendance a = attendanceRepo.findByEmployeeAndDate(e, today);
        if (a == null) {
            a = new Attendance();
            a.setEmployee(e);
            a.setDate(today);
            a.setAttendanceDate(today);
        }
        if (a.getCheckInTime() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Already punched in today");
        LocalDateTime checkIn = LocalDateTime.now();
        a.setCheckInTime(checkIn);
        // Office start and grace period are deployment settings, not hardcoded UI policy.
        java.time.LocalTime scheduledStart = shiftAssignmentRepo.findByEmployee_IdAndShiftDate(e.getId(), today)
                .map(shift -> shift.getShift().getStartTime())
                .orElseGet(() -> java.time.LocalTime.parse(officeStart));
        java.time.LocalTime lateCutoff = scheduledStart.plusMinutes(Math.max(0, attendanceGraceMinutes));
        a.setStatus(checkIn.toLocalTime().isAfter(lateCutoff)
                ? AttendanceStatus.LATE : AttendanceStatus.PRESENT);
        a.setWorkMode(parseWorkMode(body == null ? null : text(body.get("workMode"))));
        return attendanceMap(attendanceRepo.save(a));
    }

    @PostMapping("/attendance/manual")
    @Transactional
    public Map<String, Object> createManualAttendance(Authentication auth, @RequestBody Map<String, Object> body) {
        requireRoles(auth, "ADMIN", "HR");
        Long employeeId = longValue(body.get("employeeId"));
        Employee employee = employeeRepo.findById(employeeId).orElseThrow(() -> notFound("Employee"));
        LocalDate date = parseDate(text(body.get("date")), "Attendance date");
        Attendance a = attendanceRepo.findByEmployeeAndDate(employee, date);
        if (a == null) { a = new Attendance(); a.setEmployee(employee); a.setDate(date); a.setAttendanceDate(date); }
        String checkIn = text(body.get("checkInTime"));
        String checkOut = text(body.get("checkOutTime"));
        if (!checkIn.isBlank()) a.setCheckInTime(LocalDateTime.parse(checkIn));
        if (!checkOut.isBlank()) a.setCheckOutTime(java.time.LocalTime.parse(checkOut));
        if (!text(body.get("workingHours")).isBlank()) a.setWorkingHours(Double.valueOf(text(body.get("workingHours"))));
        if (!text(body.get("status")).isBlank()) a.setStatus(AttendanceStatus.valueOf(text(body.get("status")).toUpperCase()));
        if (!text(body.get("workMode")).isBlank()) a.setWorkMode(WorkMode.valueOf(text(body.get("workMode")).toUpperCase()));
        return attendanceMap(attendanceRepo.save(a));
    }

    @DeleteMapping("/attendance/manual/{id}")
    @Transactional
    public Map<String, Object> deleteManualAttendance(Authentication auth, @PathVariable Long id) {
        requireRoles(auth, "ADMIN", "HR");
        Attendance a = attendanceRepo.findById(id).orElseThrow(() -> notFound("Attendance"));
        attendanceRepo.delete(a);
        return Map.of("success", true, "id", id);
    }

    @PostMapping("/attendance/punch-out")
    @Transactional
    public Map<String, Object> punchOut(Authentication auth) {
        Employee e = currentEmployee(auth);
        expireForgottenAttendance(e);
        Attendance a = attendanceRepo.findByEmployeeAndDate(e, LocalDate.now());
        if (a == null || a.getCheckInTime() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Punch in first");
        if (a.getCheckOutTime() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Already punched out today");
        if (a.getWorkingHours() != null && a.getWorkingHours() == 0.0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This session expired after 24 hours; request attendance regularization.");
        if (java.time.Duration.between(a.getCheckInTime(), LocalDateTime.now()).toHours() >= 24) {
            a.setWorkingHours(0.0);
            a.setStatus(AttendanceStatus.UNVERIFIED);
            // Return the saved unverified record; throwing inside @Transactional would roll back the update.
            return attendanceMap(attendanceRepo.save(a));
        }
        java.time.LocalTime checkOut = java.time.LocalTime.now();
        a.setCheckOutTime(checkOut);
        a.setWorkingHours(Math.max(0, java.time.Duration.between(a.getCheckInTime(), LocalDateTime.of(LocalDate.now(), checkOut)).toMinutes() / 60.0));
        return attendanceMap(attendanceRepo.save(a));
    }

    /** Close forgotten punches after 24 hours. Zero hours require a regularization approval.
     * Do not rewrite historical records that already have a checkout or a zero-hour closure.
     */
    @Transactional
    private void expireForgottenAttendance(Employee employee) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        for (Attendance record : attendanceRepo.findByEmployee(employee)) {
            if (record.getCheckInTime() != null && record.getCheckOutTime() == null
                    && record.getWorkingHours() == null && !record.getCheckInTime().isAfter(threshold)) {
                record.setWorkingHours(0.0);
                // No checkout time is fabricated: an expired punch is not a real checkout.
                attendanceRepo.save(record);
            }
        }
    }

    // ==================== WORK FROM HOME ====================

    @GetMapping("/wfh/my")
    public List<WfhRequest> myWfh(Authentication auth) {
        return wfhRepo.findByEmployeeIdOrderByCreatedDateDesc(userId(auth));
    }

    @GetMapping("/wfh")
    public List<WfhRequest> allWfh(Authentication auth) {
        String r = role(auth);
        if (!("ADMIN".equals(r) || "HR".equals(r))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Admin or HR can view all WFH requests");
        return wfhRepo.findAll().stream().sorted(Comparator.comparing(WfhRequest::getCreatedDate, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    @GetMapping("/wfh/team")
    public List<WfhRequest> teamWfh(Authentication auth) {
        if (!"MANAGER".equals(role(auth))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only managers can view team WFH requests");
        Set<String> teamIds = employeeRepo.findByReportingManager_Id(currentEmployee(auth).getId()).stream()
                .map(e -> e.getRegisterEmployee() == null ? null : e.getRegisterEmployee().getUserId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return wfhRepo.findAll().stream().filter(x -> teamIds.contains(x.getEmployeeId()))
                .sorted(Comparator.comparing(WfhRequest::getCreatedDate, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    @PostMapping("/wfh")
    @Transactional
    public WfhRequest applyWfh(Authentication auth, @RequestBody WfhRequest req) {
        if (!"EMPLOYEE".equals(role(auth))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only employees can submit WFH requests");
        if (req.getStartDate() == null || req.getEndDate() == null) bad("Start date and end date are required");
        if (req.getEndDate().isBefore(req.getStartDate())) bad("End date cannot be before start date");
        if (text(req.getReason()).isBlank()) bad("Reason is required");
        req.setId(null); req.setEmployeeId(userId(auth)); req.setEmployeeName(currentName(auth));
        req.setWfhMode("Work From Home"); req.setStatus("PENDING");
        WfhRequest saved = wfhRepo.save(req);
        notifyReviewers(auth, "WFH request", "from " + req.getStartDate() + " to " + req.getEndDate());
        return saved;
    }

    @PatchMapping("/wfh/{id}/status")
    @Transactional
    public WfhRequest updateWfhStatus(Authentication auth, @PathVariable Long id, @RequestBody Map<String,Object> body) {
        WfhRequest req = wfhRepo.findById(id).orElseThrow(() -> notFound("WFH request"));
        String requested = text(body.get("status")).toUpperCase();
        if (!List.of("APPROVED", "REJECTED", "PENDING").contains(requested)) bad("Invalid WFH status");
        String r = role(auth);
        if ("MANAGER".equals(r)) assertCanManageUser(auth, req.getEmployeeId());
        else if (!("ADMIN".equals(r) || "HR".equals(r))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Admin, HR or the reporting manager can update WFH requests");
        req.setStatus(requested);
        WfhRequest saved = wfhRepo.save(req);
        notifyUser(req.getEmployeeId(), "Your WFH request was " + requested.toLowerCase(Locale.ENGLISH) + " by " + currentName(auth) + ".");
        return saved;
    }

    // ==================== LEAVES ====================

    @GetMapping("/leaves/my")
    public List<LeaveRequest> myLeaves(Authentication auth) { return leaveRepo.findByEmployeeId(userId(auth)); }

    @PostMapping("/leaves")
    @Transactional
    public LeaveRequest applyLeave(Authentication auth, @RequestBody LeaveRequest req) {
        if (req.getStartDate() == null || req.getEndDate() == null) bad("From and To dates are required");
        if (req.getEndDate().isBefore(req.getStartDate())) bad("To date cannot be before From date");
        if (req.getStartDate().isBefore(LocalDate.now())) bad("Leave date cannot be in the past");
        if (text(req.getReason()).trim().length() < 3) bad("Reason must contain at least 3 characters");

        String durationType = text(req.getLeaveDurationType()).isBlank()
                ? "FULL_DAY" : text(req.getLeaveDurationType()).toUpperCase();
        if (!List.of("FULL_DAY", "FIRST_HALF", "SECOND_HALF").contains(durationType)) {
            bad("Invalid leave type. Choose Full Day, First Half or Second Half");
        }
        if (!"FULL_DAY".equals(durationType) && !req.getStartDate().equals(req.getEndDate())) {
            bad("Half-day leave can only be requested for a single date");
        }
        req.setLeaveDurationType(durationType);
        // A Short Leave is selected solely by FIRST_HALF / SECOND_HALF. Do not
        // require or trust a category from the client (including stale values).
        boolean shortLeave = LeaveWorkflowRules.isShortLeave(durationType);
        if (!shortLeave && text(req.getLeaveType()).isBlank()) bad("Leave category is required");

        String cc = text(req.getCcEmailTo()).trim();
        if (!cc.isBlank()) {
            for (String email : cc.split(",")) {
                String value = email.trim();
                if (!value.isBlank() && !value.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                    bad("Invalid CC email: " + value);
                }
            }
            req.setCcEmailTo(cc);
        } else {
            req.setCcEmailTo(null);
        }

        Employee employee = currentEmployee(auth);
        double requestedDays = req.getTotalDays();
        boolean overlapping = leaveRepo.findByEmployeeIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        userId(auth), req.getEndDate(), req.getStartDate()).stream()
                .anyMatch(existing -> (existing.getStatus() == LeaveStatus.PENDING || existing.getStatus() == LeaveStatus.APPROVED)
                        && LeaveWorkflowRules.conflicts(req.getStartDate(), req.getEndDate(), durationType,
                            existing.getStartDate(), existing.getEndDate(), existing.getLeaveDurationType()));
        if (overlapping) bad("You already have a pending or approved leave request overlapping this period");

        // Short Leave is a separate workflow, not a Casual Leave subtype. Its
        // descriptive label is stored on the request, not in the leave catalogue,
        // and it must not debit a regular leave balance.
        LeaveType type = shortLeave ? null : leaveTypeRepo.findByName(req.getLeaveType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid leave category"));
        if (!shortLeave) {
            LeaveBalance balance = leaveBalanceRepo.findByEmployeeIdAndLeaveTypeIdAndYear(
                    employee.getId(), type.getId(), req.getStartDate().getYear()).orElse(null);
            String leaveCode = text(type.getCode()).toUpperCase(Locale.ENGLISH);
            boolean balanceControlled = !List.of("PROJECT_OFF", "LEAVE_WITHOUT_PAY").contains(leaveCode);
            if (balanceControlled && balance != null && balance.getBalance() < requestedDays) {
                bad("Insufficient leave balance. Available: " + balance.getBalance());
            }
        }

        Employee reportingManager = employee.getReportingManager();
        RegisterEmployee managerAccount = reportingManager == null ? null : reportingManager.getRegisterEmployee();

        req.setId(null);
        req.setEmployeeId(userId(auth));
        req.setEmployeeName(currentName(auth));
        req.setLeaveType(shortLeave ? "Short Leave" : type.getName());
        req.setReportingManagerName(reportingManager == null ? null :
                (text(reportingManager.getFullName()).isBlank() ?
                        (managerAccount == null ? null : managerAccount.getName()) : reportingManager.getFullName()));
        req.setReportingManagerEmail(reportingManager == null ? null :
                (text(reportingManager.getEmail()).isBlank() ?
                        (managerAccount == null ? null : managerAccount.getEmail()) : reportingManager.getEmail()));
        String requestedManagerEmail = text(req.getManagerNotificationEmail()).trim();
        String applicantRole = role(auth);
        if ("EMPLOYEE".equals(applicantRole)) {
            // Employees can only notify their actual reporting manager.
            String assignedManagerEmail = text(req.getReportingManagerEmail()).trim();
            if (!requestedManagerEmail.isBlank() &&
                    (assignedManagerEmail.isBlank() || !requestedManagerEmail.equalsIgnoreCase(assignedManagerEmail))) {
                bad("Manager email must match your assigned reporting manager's verified email");
            }
            if (requestedManagerEmail.isBlank()) requestedManagerEmail = assignedManagerEmail;
        } else {
            // Managers may optionally copy an HR/Admin, and HR may optionally copy an Admin.
            // A client-supplied arbitrary address must never become a reviewer.
            boolean eligibleRecipient = false;
            for (RegisterEmployee reviewer : registerRepo.findAll()) {
                String reviewerRole = normalizeRole(reviewer.getRole());
                if (requestedManagerEmail.equalsIgnoreCase(text(reviewer.getEmail())) &&
                        ("MANAGER".equals(applicantRole) && Set.of("HR", "ADMIN").contains(reviewerRole)
                                || "HR".equals(applicantRole) && "ADMIN".equals(reviewerRole))) {
                    eligibleRecipient = true;
                    break;
                }
            }
            if (!requestedManagerEmail.isBlank() && !eligibleRecipient) {
                bad("Choose an existing authorized HR/Admin reviewer, or leave the reviewer blank");
            }
            req.setReportingManagerName(null);
            req.setReportingManagerEmail(null);
        }
        req.setManagerNotificationEmail(requestedManagerEmail.isBlank() ? null : requestedManagerEmail);
        req.setStatus(LeaveStatus.PENDING);
        req.setCreatedAt(LocalDateTime.now());
        req.setApprovalComment(null);
        req.setApprovedAt(null);
        LeaveRequest saved = leaveRepo.save(req);
        notifyLeaveReviewers(auth, saved);
        sendLeaveEmail(saved);
        return saved;
    }

    // Administrators can see all employee leave records, but only HR/Manager
    // requests contain approval controls. Each row includes the server's decision.
    @GetMapping("/leaves")
    public List<Map<String, Object>> allLeaves(Authentication auth) {
        return leaveRepo.findAll().stream().map(r -> leaveReviewMap(auth, r)).toList();
    }

    @GetMapping("/leaves/team")
    public List<Map<String, Object>> teamLeaves(Authentication auth) {
        Set<String> ids = employeeRepo.findByReportingManager_Id(currentEmployee(auth).getId()).stream()
                .map(e -> e.getRegisterEmployee() == null ? null : e.getRegisterEmployee().getUserId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return leaveRepo.findAll().stream().filter(r -> ids.contains(r.getEmployeeId()))
                .map(r -> leaveReviewMap(auth, r)).toList();
    }

    @PatchMapping("/leaves/{id}/status")
    @Transactional
    public LeaveRequest updateLeave(Authentication auth, @PathVariable Long id, @RequestBody Map<String, String> body) {
        LeaveRequest r = leaveRepo.findById(id).orElseThrow(() -> notFound("Leave request"));
        if (Objects.equals(userId(auth), r.getEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot approve or reject your own leave request");
        }
        assertCanReviewWorkflow(auth, r.getEmployeeId());
        String status = text(body.get("status")).toUpperCase(Locale.ENGLISH);
        String comment = text(body.get("approvalComment"));
        if (comment.isBlank()) comment = text(body.get("comment"));
        if (r.getStatus() != LeaveStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending leave requests can be reviewed");
        }
        if ("REJECTED".equals(status) && comment.length() < 3) {
            bad("Please provide at least 3 characters explaining the rejection");
        }
        if (comment.length() > 1000) bad("Review note must be 1000 characters or fewer");
        if ("APPROVED".equals(status)) {
            LeaveRequest saved = adminLeaveService.approveLeave(id, comment.isBlank() ? null : comment);
            notifyUser(saved.getEmployeeId(), "Your " + saved.getLeaveType() + " leave request was approved by " + currentName(auth) + ".");
            return saved;
        }
        if ("REJECTED".equals(status)) {
            // No balance change on rejection. Save in this API transaction to avoid
            // cross-controller service behavior from masking the review result.
            r.setStatus(LeaveStatus.REJECTED);
            r.setApprovalComment(comment);
            r.setApprovedAt(LocalDateTime.now());
            LeaveRequest saved = leaveRepo.saveAndFlush(r);
            notifyUser(saved.getEmployeeId(), "Your " + saved.getLeaveType() + " leave request was rejected by " + currentName(auth) + ": " + comment);
            return saved;
        }
        bad("Only APPROVED or REJECTED is allowed");
        return r;
    }

    @PatchMapping("/leaves/{id}/cancel")
    @Transactional
    public LeaveRequest cancelLeave(Authentication auth, @PathVariable Long id) {
        LeaveRequest r = leaveRepo.findById(id).orElseThrow(() -> notFound("Leave request"));
        if (!userId(auth).equals(r.getEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own leave request");
        }
        if (r.getStatus() != LeaveStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending leave requests can be cancelled");
        }
        r.setStatus(LeaveStatus.CANCELLED);
        r.setApprovalComment("Cancelled by employee");
        r.setApprovedAt(LocalDateTime.now());
        return leaveRepo.save(r);
    }

    @GetMapping("/leave-balances/my")
    public List<Map<String, Object>> myLeaveBalances(Authentication auth) {
        String currentUserId = userId(auth);
        List<LeaveRequest> approved = leaveRepo.findByEmployeeIdAndStatus(currentUserId, LeaveStatus.APPROVED);
        return adminLeaveService.getEmployeeBalances(currentUserId).stream().map(b -> {
            String typeName = b.getLeaveType() == null ? null : b.getLeaveType().getName();
            double availed = approved.stream()
                    .filter(r -> r.getStartDate() != null && Objects.equals(r.getStartDate().getYear(), b.getYear()))
                    .filter(r -> Objects.equals(r.getLeaveType(), typeName))
                    .mapToDouble(LeaveRequest::getTotalDays)
                    .sum();
            double available = b.getBalance() == null ? 0.0 : b.getBalance();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("year", b.getYear());
            m.put("balance", available);
            m.put("available", available);
            m.put("availed", availed);
            m.put("entitlement", available + availed);
            m.put("leaveType", typeName);
            m.put("code", b.getLeaveType() == null ? null : b.getLeaveType().getCode());
            m.put("defaultDays", b.getLeaveType() == null ? null : b.getLeaveType().getDefaultDays());
            return m;
        }).toList();
    }

    @GetMapping("/leaves/context")
    public Map<String, Object> leaveContext(Authentication auth) {
        Employee employee = currentEmployee(auth);
        Employee manager = employee.getReportingManager();
        RegisterEmployee managerAccount = manager == null ? null : manager.getRegisterEmployee();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeId", userId(auth));
        result.put("employeeName", currentName(auth));
        result.put("designation", employee.getRegisterEmployee() == null ? null : employee.getRegisterEmployee().getDesignation());
        result.put("projectManager", manager == null ? null :
                (text(manager.getFullName()).isBlank() ? (managerAccount == null ? null : managerAccount.getName()) : manager.getFullName()));
        result.put("projectManagerPhone", manager == null ? null :
                (managerAccount != null && !text(managerAccount.getPhoneNumber()).isBlank()
                        ? managerAccount.getPhoneNumber() : manager.getPhone()));
        result.put("projectManagerEmail", manager == null ? null :
                (text(manager.getEmail()).isBlank() ? (managerAccount == null ? null : managerAccount.getEmail()) : manager.getEmail()));
        String applicantRole = role(auth);
        if ("MANAGER".equals(applicantRole) || "HR".equals(applicantRole)) {
            result.put("optionalReviewers", registerRepo.findAll().stream()
                    .filter(account -> !text(account.getEmail()).isBlank())
                    .filter(account -> "MANAGER".equals(applicantRole)
                            ? Set.of("ADMIN", "HR").contains(normalizeRole(account.getRole()))
                            : "ADMIN".equals(normalizeRole(account.getRole())))
                    .filter(account -> !Objects.equals(account.getUserId(), userId(auth)))
                    .map(account -> Map.of("email", account.getEmail(), "label",
                            text(account.getName()).isBlank() ? normalizeRole(account.getRole()) : account.getName(),
                            "role", normalizeRole(account.getRole())))
                    .toList());
        } else result.put("optionalReviewers", List.of());
        return result;
    }

    // Admin leave management backed by the existing service
    @GetMapping("/leave-types")
    public List<LeaveType> leaveTypes() { return adminLeaveService.findAllLeaveTypes(); }

    @GetMapping("/leave-types/active")
    public List<LeaveType> activeLeaveTypes() {
        Set<String> employeeLeaveTypes = Set.of("CASUAL_LEAVE", "PROJECT_OFF", "LEAVE_WITHOUT_PAY");
        return adminLeaveService.findAllLeaveTypes().stream()
                .filter(t -> !Boolean.FALSE.equals(t.getIsActive()))
                .filter(t -> employeeLeaveTypes.contains(text(t.getCode()).toUpperCase(Locale.ENGLISH)))
                .sorted(Comparator.comparingInt(t -> switch (text(t.getCode()).toUpperCase(Locale.ENGLISH)) {
                    case "CASUAL_LEAVE" -> 1;
                    case "PROJECT_OFF" -> 2;
                    case "LEAVE_WITHOUT_PAY" -> 3;
                    default -> 99;
                }))
                .toList();
    }

    @PostMapping("/leave-types")
    public LeaveType createLeaveType(@RequestBody LeaveType type) { type.setId(null); return adminLeaveService.saveLeaveType(type); }

    @PutMapping("/leave-types/{id}")
    public LeaveType updateLeaveType(@PathVariable Long id, @RequestBody LeaveType type) { type.setId(id); return adminLeaveService.saveLeaveType(type); }

    @DeleteMapping("/leave-types/{id}")
    public Map<String, Object> deleteLeaveType(@PathVariable Long id) { adminLeaveService.deleteLeaveType(id); return Map.of("success", true); }

    @GetMapping("/leave-policies")
    public List<LeavePolicy> leavePolicies() { return adminLeaveService.findAllPolicies(); }

    @PostMapping("/leave-policies")
    public LeavePolicy createLeavePolicy(@RequestBody LeavePolicy policy) { policy.setId(null); return adminLeaveService.savePolicy(policy); }

    @PutMapping("/leave-policies/{id}")
    public LeavePolicy updateLeavePolicy(@PathVariable Long id, @RequestBody LeavePolicy policy) { policy.setId(id); return adminLeaveService.savePolicy(policy); }

    @DeleteMapping("/leave-policies/{id}")
    public Map<String, Object> deleteLeavePolicy(@PathVariable Long id) { adminLeaveService.deletePolicy(id); return Map.of("success", true); }

//    @GetMapping("/holidays")
//    public List<Holiday> holidays() { return adminLeaveService.findAllHolidays(); }
//
//    @PostMapping("/holidays")
//    public Holiday createHoliday(@RequestBody Holiday holiday) { holiday.setId(null); return adminLeaveService.saveHoliday(holiday); }
//
//    @DeleteMapping("/holidays/{id}")
//    public Map<String, Object> deleteHoliday(@PathVariable Long id) { adminLeaveService.deleteHoliday(id); return Map.of("success", true); }

    @PostMapping("/leave-balance/adjust")
    public Map<String, Object> adjustLeaveBalance(@RequestBody LeaveBalanceAdjustDTO dto) {
        adminLeaveService.adjustLeaveBalance(dto);
        return Map.of("success", true);
    }

    @GetMapping("/leave-balances/{employeeId}")
    public List<LeaveBalance> leaveBalances(@PathVariable String employeeId) { return adminLeaveService.getEmployeeBalances(employeeId); }

    // ==================== TIMESHEETS ====================

    @GetMapping("/timesheets/my")
    public List<Map<String, Object>> myTimesheets(Authentication auth) {
        return timesheetRepo.findByEmployeeIdOrderByDateDesc(userId(auth)).stream().map(this::timesheetMap).toList();
    }

    @GetMapping("/timesheets")
    public List<Map<String, Object>> allTimesheets(Authentication auth) {
        String reviewerRole = role(auth);
        // No employee timesheet data is returned to Admin. HR handles employee
        // timesheets; managers use /timesheets/team for their direct reports.
        if (!"ADMIN".equals(reviewerRole) && !"HR".equals(reviewerRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Use your team timesheet view");
        }
        return timesheetRepo.findAll().stream()
                .filter(t -> !"ADMIN".equals(reviewerRole)
                        || WorkflowApprovalPolicy.adminCanViewTimesheet(accountRole(t.getEmployeeId())))
                .sorted(Comparator.comparing(Timesheet::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> timesheetReviewMap(auth, t)).toList();
    }

    @GetMapping("/timesheets/team")
    public List<Map<String, Object>> teamTimesheets(Authentication auth) {
        String currentUserId = userId(auth);
        Set<String> ids = employeeRepo.findByReportingManager_Id(currentEmployee(auth).getId()).stream()
                .map(e -> e.getRegisterEmployee() == null ? null : e.getRegisterEmployee().getUserId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        // Managers can fill their own timesheet from the same page while also
        // reviewing direct reports. Keep their own entries in the result set.
        ids.add(currentUserId);
        return timesheetRepo.findAll().stream()
                .filter(t -> ids.contains(t.getEmployeeId()))
                .sorted(Comparator.comparing(Timesheet::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> timesheetReviewMap(auth, t)).toList();
    }

    /** Timesheets submitted by learners/trainees currently assigned to the logged-in senior mentor. */
    @GetMapping("/timesheets/mentee")
    public List<Map<String, Object>> menteeTimesheets(Authentication auth) {
        if ("ADMIN".equals(role(auth))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee timesheets are managed by HR and Managers");
        }
        Employee mentor = currentEmployee(auth);
        Set<Long> assignmentIds = mentorshipRepo.findByMentor_IdOrderByCreatedAtDesc(mentor.getId())
                .stream().map(MentorshipAssignment::getId).collect(Collectors.toSet());
        if (assignmentIds.isEmpty()) return List.of();
        return timesheetRepo.findAll().stream()
                .filter(t -> t.getMentorshipAssignment() != null && assignmentIds.contains(t.getMentorshipAssignment().getId()))
                .sorted(Comparator.comparing(Timesheet::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::timesheetMap).toList();
    }

    @GetMapping("/timesheets/context")
    public Map<String, Object> timesheetContext(Authentication auth,
                                                 @RequestParam(required = false) Integer year,
                                                 @RequestParam(required = false) Integer month) {
        LocalDate today = LocalDate.now();
        int resolvedYear = year == null ? today.getYear() : year;
        int resolvedMonth = month == null ? today.getMonthValue() : month;
        YearMonth ym;
        try { ym = YearMonth.of(resolvedYear, resolvedMonth); }
        catch (Exception ex) { throw badException("Invalid timesheet month"); }

        Employee employee = currentEmployee(auth);
        Employee manager = employee.getReportingManager();
        LocalDate cutoffDate = ym.plusMonths(1).atDay(2);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeId", userId(auth));
        result.put("employeeName", currentName(auth));
        result.put("managerName", manager == null ? null : manager.getFullName());
        result.put("managerId", manager == null ? null : manager.getId());
        result.put("month", ym.getMonthValue());
        result.put("year", ym.getYear());
        result.put("cutoffDate", cutoffDate);
        result.put("cutoffPassed", today.isAfter(cutoffDate));
        result.put("message", "Complete all required daily timesheets by " + cutoffDate + ". Missing working days are treated as unpaid absence for payroll.");
        return result;
    }

    @GetMapping("/timesheets/calendar")
    public List<Map<String, Object>> timesheetCalendar(Authentication auth,
                                                        @RequestParam Integer year,
                                                        @RequestParam Integer month) {
        YearMonth ym;
        try { ym = YearMonth.of(year, month); }
        catch (Exception ex) { throw badException("Invalid timesheet month"); }

        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
        LocalDate today = LocalDate.now();
        LocalDate cutoffDate = ym.plusMonths(1).atDay(2);
        boolean cutoffPassed = today.isAfter(cutoffDate);
        String currentUserId = userId(auth);

        Map<LocalDate, List<Timesheet>> logsByDate = timesheetRepo
                .findByEmployeeIdAndDateBetween(currentUserId, first, last).stream()
                .collect(Collectors.groupingBy(Timesheet::getDate));
        List<LeaveRequest> approvedLeaves = leaveRepo
                .findByEmployeeIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(currentUserId, last, first).stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .toList();
        Map<LocalDate, Holiday> holidays = holidayRepo.findByHolidayDateBetween(first, last).stream()
                .filter(this::isImportantHoliday)
                .collect(Collectors.toMap(Holiday::getHolidayDate, h -> h, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate date = ym.atDay(day);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("cutoffDate", cutoffDate);
            item.put("locked", cutoffPassed);

            Holiday holiday = holidays.get(date);
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            LeaveRequest leave = approvedLeaves.stream()
                    .filter(l -> !date.isBefore(l.getStartDate()) && !date.isAfter(l.getEndDate()))
                    .findFirst().orElse(null);
            List<Timesheet> dayLogs = logsByDate.getOrDefault(date, List.of());
            List<Timesheet> validLogs = dayLogs.stream()
                    .filter(t -> !"REJECTED".equalsIgnoreCase(text(t.getStatus())))
                    .toList();
            double hours = validLogs.stream().mapToDouble(t -> t.getHoursWorked() == null ? 0.0 : t.getHoursWorked()).sum();
            String aggregateStatus = validLogs.stream().anyMatch(t -> "APPROVED".equalsIgnoreCase(text(t.getStatus())))
                    ? "APPROVED" : validLogs.isEmpty() ? null : "PENDING";

            String state;
            String label;
            boolean required = false;
            boolean canFill = false;

            if (holiday != null) {
                state = "HOLIDAY"; label = holiday.getName();
            } else if (weekend) {
                state = "WEEKEND"; label = "Weekend";
            } else if (leave != null) {
                boolean unpaid = isUnpaidLeave(leave.getLeaveType());
                boolean projectOff = text(leave.getLeaveType()).toUpperCase(Locale.ENGLISH).contains("PROJECT OFF");
                state = unpaid ? "LWP" : projectOff ? "PROJECT_OFF" : "LEAVE";
                label = unpaid ? "Leave Without Pay" : projectOff ? "Project Off" : text(leave.getLeaveType());
            } else if (date.isAfter(today)) {
                state = "FUTURE"; label = "Upcoming";
                required = true;
            } else if (!validLogs.isEmpty()) {
                state = "PRESENT"; label = aggregateStatus == null ? "Timesheet Filled" : "Timesheet " + aggregateStatus.substring(0, 1) + aggregateStatus.substring(1).toLowerCase(Locale.ENGLISH);
                required = true;
                canFill = !cutoffPassed && !"APPROVED".equalsIgnoreCase(aggregateStatus);
            } else {
                state = "ABSENT";
                label = dayLogs.stream().anyMatch(t -> "REJECTED".equalsIgnoreCase(text(t.getStatus()))) ? "Timesheet Rejected" : "Timesheet Missing";
                required = true;
                canFill = !cutoffPassed;
            }

            item.put("state", state);
            item.put("label", label);
            item.put("required", required);
            item.put("canFill", canFill);
            item.put("hours", hours);
            item.put("timesheetStatus", aggregateStatus);
            item.put("entryCount", validLogs.size());
            result.add(item);
        }
        return result;
    }

    @PostMapping("/timesheets")
    @Transactional
    public Map<String, Object> createTimesheet(Authentication auth, @RequestBody Map<String, Object> body) {
        // Parse request fields explicitly instead of converting the whole map into
        // the JPA entity. This keeps LocalDate handling deterministic and prevents
        // clients from binding server-managed/entity relationship fields.
        Timesheet t = new Timesheet();
        t.setDate(parseDate(body.get("date"), "Timesheet date"));
        validateTimesheetEntryDate(t.getDate());
        try {
            t.setHoursWorked(Double.valueOf(text(body.get("hoursWorked"))));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hours worked must be a valid number");
        }
        t.setTaskDescription(text(body.get("taskDescription")));
        t.setWorkMode(textOrNull(body.get("workMode")));
        t.setEmployeeId(userId(auth));
        t.setEmployeeName(currentName(auth));
        t.setStatus("PENDING");
        attachTimesheetLearningAndProject(auth, t, body);
        EmployeeProfile profile = profileRepo.findByUserId(userId(auth)).orElse(null);
        t.setEmployee(profile);
        if (t.getHoursWorked() == null || t.getHoursWorked() <= 0 || t.getHoursWorked() > 24) bad("Hours worked must be between 0 and 24");
        if (text(t.getTaskDescription()).isBlank()) bad("Task description is required");
        Timesheet saved = timesheetRepo.save(t);
        String detail = saved.getDate() + " · " + saved.getHoursWorked() + "h" + (saved.getProject() == null ? " · General / Non-Project Work" : " · " + saved.getProject().getProjectName());
        notifyReviewers(auth, "timesheet", detail);
        return timesheetMap(saved);
    }

    @PutMapping("/timesheets/{id}")
    @Transactional
    public Map<String, Object> editTimesheet(Authentication auth, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Timesheet t = timesheetRepo.findById(id).orElseThrow(() -> notFound("Timesheet"));
        if (!Objects.equals(t.getEmployeeId(), userId(auth))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own timesheet");
        }
        String existingStatus = text(t.getStatus()).toUpperCase(Locale.ENGLISH);
        if (!List.of("PENDING", "REJECTED").contains(existingStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending or rejected timesheets can be edited");
        }
        if (body.containsKey("date")) t.setDate(parseDate(body.get("date"), "Timesheet date"));
        if (body.containsKey("hoursWorked")) t.setHoursWorked(Double.valueOf(text(body.get("hoursWorked"))));
        if (body.containsKey("taskDescription")) t.setTaskDescription(text(body.get("taskDescription")));
        if (body.containsKey("workMode")) t.setWorkMode(text(body.get("workMode")));
        attachTimesheetLearningAndProject(auth, t, body);
        if (t.getDate() == null) bad("Timesheet date is required");
        validateTimesheetEntryDate(t.getDate());
        if (t.getHoursWorked() == null || t.getHoursWorked() <= 0 || t.getHoursWorked() > 24) bad("Hours worked must be between 0 and 24");
        if (text(t.getTaskDescription()).isBlank()) bad("Task description is required");
        if ("REJECTED".equals(existingStatus)) {
            t.setStatus("PENDING");
            t.setApprovalComment(null);
            t.setManagerReviewedBy(null);
            t.setManagerReviewedAt(null);
            t.setMentorReviewStatus(null);
            t.setMentorReviewComment(null);
            t.setMentorReviewedBy(null);
            t.setMentorReviewedAt(null);
        }
        Timesheet saved = timesheetRepo.save(t);
        String detail = saved.getDate() + " · " + saved.getHoursWorked() + "h" + (saved.getProject() == null ? " · General / Non-Project Work" : " · " + saved.getProject().getProjectName());
        notifyReviewers(auth, "timesheet update", detail);
        return timesheetMap(saved);
    }

    @DeleteMapping("/timesheets/{id}")
    @Transactional
    public Map<String, Object> deleteTimesheet(Authentication auth, @PathVariable Long id) {
        Timesheet t = timesheetRepo.findById(id).orElseThrow(() -> notFound("Timesheet"));
        String r = role(auth);
        boolean owner = Objects.equals(t.getEmployeeId(), userId(auth));
        if (owner) {
            if (!"PENDING".equalsIgnoreCase(text(t.getStatus()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only your pending timesheets can be deleted");
            }
        } else if (!"HR".equals(r)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin may review Manager/HR timesheets but cannot delete timesheets");
        }
        timesheetRepo.delete(t);
        return Map.of("success", true);
    }

    @PatchMapping("/timesheets/{id}/mentor-review")
    @Transactional
    public Map<String, Object> reviewTimesheetAsMentor(Authentication auth, @PathVariable Long id, @RequestBody Map<String, String> body) {
        if ("ADMIN".equals(role(auth))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin cannot review employee learning timesheets");
        }
        Timesheet t = timesheetRepo.findById(id).orElseThrow(() -> notFound("Timesheet"));
        MentorshipAssignment learning = t.getMentorshipAssignment();
        if (learning == null || learning.getMentor() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This is not a learning/shadow timesheet");
        }
        Employee reviewer = currentEmployee(auth);
        if (!Objects.equals(learning.getMentor().getId(), reviewer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned senior mentor can review this learning timesheet");
        }
        if (!"PENDING".equalsIgnoreCase(text(t.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending timesheets can be reviewed");
        }
        String status = text(body.get("status")).toUpperCase();
        if (!List.of("APPROVED", "REJECTED").contains(status)) bad("Mentor review status must be APPROVED or REJECTED");
        String comment = text(body.get("comment"));
        if ("REJECTED".equals(status) && comment.isBlank()) bad("Please explain what the learner needs to correct");
        t.setMentorReviewStatus(status);
        t.setMentorReviewComment(comment.isBlank() ? null : comment);
        t.setMentorReviewedBy(currentName(auth));
        t.setMentorReviewedAt(LocalDateTime.now());
        Timesheet saved = timesheetRepo.save(t);
        notifyUser(saved.getEmployeeId(), "Your learning timesheet for " + saved.getDate() + " was " + status.toLowerCase(Locale.ENGLISH) + " by senior mentor " + currentName(auth) + ".");
        if ("APPROVED".equals(status)) notifyReportingManagerOf(saved.getEmployeeId(), "Senior mentor approved " + saved.getEmployeeName() + "'s learning timesheet for " + saved.getDate() + ". Final review is ready.");
        return timesheetMap(saved);
    }

    @PatchMapping("/timesheets/{id}/status")
    @Transactional
    public Map<String, Object> updateTimesheet(Authentication auth, @PathVariable Long id, @RequestBody Map<String, String> body) {
        Timesheet t = timesheetRepo.findById(id).orElseThrow(() -> notFound("Timesheet"));
        if (Objects.equals(userId(auth), t.getEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot approve or reject your own timesheet");
        }
        assertCanReviewWorkflow(auth, t.getEmployeeId());
        String current = text(t.getStatus()).toUpperCase();
        if (!"PENDING".equals(current)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending timesheets can be approved or rejected");
        }
        String status = text(body.get("status")).toUpperCase();
        if (!List.of("APPROVED", "REJECTED").contains(status)) bad("Status must be APPROVED or REJECTED");
        String comment = text(body.get("comment"));
        if (comment.isBlank()) comment = text(body.get("approvalComment"));
        if ("REJECTED".equals(status) && comment.length() < 3)
            bad("Please provide at least 3 characters explaining the timesheet rejection");
        if (comment.length() > 1000) bad("Review note must be 1000 characters or fewer");

        MentorshipAssignment learning = t.getMentorshipAssignment();
        if ("APPROVED".equals(status) && learning != null && !"APPROVED".equalsIgnoreCase(text(t.getMentorReviewStatus()))) {
            Employee reviewer = findCurrentEmployee(auth).orElse(null);
            // If the reporting Manager is also the assigned mentor, one approval represents both checks.
            if (reviewer != null && learning.getMentor() != null && learning.getManager() != null
                    && Objects.equals(reviewer.getId(), learning.getMentor().getId())
                    && Objects.equals(reviewer.getId(), learning.getManager().getId())) {
                t.setMentorReviewStatus("APPROVED");
                t.setMentorReviewedBy(currentName(auth));
                t.setMentorReviewedAt(LocalDateTime.now());
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The assigned senior mentor must review this learning timesheet before final Manager approval");
            }
        }

        t.setStatus(status);
        t.setApprovalComment(comment.isBlank() ? null : comment);
        t.setManagerReviewedBy(currentName(auth));
        t.setManagerReviewedAt(LocalDateTime.now());
        Timesheet saved = timesheetRepo.saveAndFlush(t);
        notifyUser(saved.getEmployeeId(), "Your timesheet for " + saved.getDate() + " was " + status.toLowerCase(Locale.ENGLISH) + " by " + currentName(auth) + (comment.isBlank() ? "." : ": " + comment));
        return timesheetMap(saved);
    }

    // ==================== PROJECTS ====================

    @GetMapping("/projects/my")
    public List<Map<String, Object>> myProjects(Authentication auth) {
        Employee current = findCurrentEmployee(auth).orElse(null);
        if (current == null) return List.of();
        Map<Long, Project> visible = new LinkedHashMap<>();
        projectRepo.findProjectsByEmployeeId(current.getId()).forEach(p -> visible.put(p.getId(), p));
        projectRepo.findByProjectManagerId(current.getId()).forEach(p -> visible.put(p.getId(), p));
        mentorshipRepo.findByMentee_IdAndStatusIgnoreCaseOrderByCreatedAtDesc(current.getId(), "ACTIVE").stream()
                .map(MentorshipAssignment::getProject).filter(Objects::nonNull)
                .forEach(p -> visible.put(p.getId(), p));
        return visible.values().stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE || p.getStatus() == ProjectStatus.IN_PROGRESS)
                .map(this::projectMap)
                .toList();
    }

    @GetMapping("/projects")
    public List<Map<String, Object>> projects(Authentication auth) {
        String r = role(auth);
        List<Project> list;
        if ("EMPLOYEE".equals(r)) {
            Employee current = findCurrentEmployee(auth).orElse(null);
            list = current == null ? List.of() : projectRepo.findProjectsByEmployeeId(current.getId());
        } else {
            // Managers need the complete project catalogue so they can assign
            // their own direct reports to any active project. HR/Admin retain
            // the full project-management view as before.
            list = projectRepo.findAllByOrderByStatusAsc();
        }
        return list.stream().map(this::projectMap).toList();
    }

    @PostMapping("/projects")
    @Transactional
    public Map<String, Object> createProject(Authentication auth, @RequestBody Map<String, Object> body) {
        Project p = new Project();
        p.setId(null);
        p.setProjectName(text(body.get("projectName")));
        p.setClientName(textOrNull(body.get("clientName")));
        p.setClientRegion(textOrNull(body.get("clientRegion")));
        p.setTechnology(textOrNull(body.get("technology")));
        p.setProjectType(textOrNull(body.get("projectType")));
        p.setDescription(textOrNull(body.get("description")));
        p.setTotalCost(body.get("totalCost") == null || text(body.get("totalCost")).isBlank() ? null : Double.valueOf(text(body.get("totalCost"))));
        p.setOnboardingDate(parseOptionalDate(body.get("onboardingDate")));
        p.setEndDate(parseOptionalDate(body.get("endDate")));
        String status = text(body.get("status"));
        if (!status.isBlank()) {
            try { p.setStatus(ProjectStatus.valueOf(status.toUpperCase())); }
            catch (IllegalArgumentException e) { throw badException("Invalid project status"); }
        } else {
            p.setStatus(ProjectStatus.ACTIVE);
        }
        Long departmentId = longValue(body.get("departmentId"));
        if (departmentId != null) {
            if (!departmentRepo.existsById(departmentId)) throw badException("Department not found");
            p.setDepartmentId(departmentId);
        }
        Long profileId = longValue(body.get("employeeProfileId"));
        if (profileId != null) {
            EmployeeProfile profile = profileRepo.findById(profileId).orElseThrow(() -> badException("Employee not found"));
            Employee employee = profile.getEmployee();
            if (employee == null) throw badException("Employee account is not linked to the selected profile");
            p.setEmployeeProfileId(profileId);
            p.setAssignedEmployeeId(employee.getId());
            p.setAssignedEmployeeName(profile.getFullName());
            if (p.getAssignedEmployees() == null) p.setAssignedEmployees(new ArrayList<>());
            p.getAssignedEmployees().add(employee);
        }
        applyProjectManagerFromBody(p, body);
        p.setCreatedBy(currentName(auth));
        p.setCreatedAt(LocalDateTime.now());
        return projectMap(projectRepo.save(p));
    }

    @PutMapping("/projects/{id}")
    @Transactional
    public Map<String, Object> updateProject(Authentication auth, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Project p = projectRepo.findById(id).orElseThrow(() -> notFound("Project"));
        if (body.containsKey("projectName")) p.setProjectName(text(body.get("projectName")));
        if (body.containsKey("clientName")) p.setClientName(textOrNull(body.get("clientName")));
        if (body.containsKey("clientRegion")) p.setClientRegion(textOrNull(body.get("clientRegion")));
        if (body.containsKey("technology")) p.setTechnology(textOrNull(body.get("technology")));
        if (body.containsKey("projectType")) p.setProjectType(textOrNull(body.get("projectType")));
        if (body.containsKey("description")) p.setDescription(textOrNull(body.get("description")));
        if (body.containsKey("totalCost")) p.setTotalCost(body.get("totalCost") == null || text(body.get("totalCost")).isBlank() ? null : Double.valueOf(text(body.get("totalCost"))));
        if (body.containsKey("onboardingDate")) p.setOnboardingDate(parseOptionalDate(body.get("onboardingDate")));
        if (body.containsKey("endDate")) p.setEndDate(parseOptionalDate(body.get("endDate")));
        if (body.containsKey("status") && !text(body.get("status")).isBlank()) {
            try { p.setStatus(ProjectStatus.valueOf(text(body.get("status")).toUpperCase())); }
            catch (IllegalArgumentException e) { throw badException("Invalid project status"); }
        }
        if (body.containsKey("departmentId")) {
            Long departmentId = longValue(body.get("departmentId"));
            if (departmentId != null && !departmentRepo.existsById(departmentId)) throw badException("Department not found");
            p.setDepartmentId(departmentId);
        }
        if (body.containsKey("employeeProfileId")) {
            Long profileId = longValue(body.get("employeeProfileId"));
            p.setEmployeeProfileId(profileId);
            if (profileId == null) {
                p.setAssignedEmployeeId(null);
                p.setAssignedEmployeeName(null);
                if (p.getAssignedEmployees() != null) p.getAssignedEmployees().clear();
            } else {
                EmployeeProfile profile = profileRepo.findById(profileId).orElseThrow(() -> badException("Employee not found"));
                Employee employee = profile.getEmployee();
                if (employee == null) throw badException("Employee account is not linked to the selected profile");
                p.setAssignedEmployeeId(employee.getId());
                p.setAssignedEmployeeName(profile.getFullName());
                if (p.getAssignedEmployees() == null) p.setAssignedEmployees(new ArrayList<>());
                p.getAssignedEmployees().clear();
                p.getAssignedEmployees().add(employee);
            }
        }
        applyProjectManagerFromBody(p, body);
        p.setUpdatedAt(LocalDateTime.now());
        return projectMap(projectRepo.save(p));
    }

    @PostMapping("/projects/{id}/assign")
    @Transactional
    public Map<String, Object> assignProjectEmployees(Authentication auth, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Project p = projectRepo.findById(id).orElseThrow(() -> notFound("Project"));
        Object raw = body.get("employeeIds");
        List<Long> ids = new ArrayList<>();
        if (raw instanceof Collection<?> c) for (Object x : c) { Long v = longValue(x); if (v != null) ids.add(v); }
        List<Employee> selected = employeeRepo.findAllById(ids);
        if (selected.size() != ids.stream().distinct().count()) throw badException("One or more employees were not found");

        if (p.getAssignedEmployees() == null) p.setAssignedEmployees(new ArrayList<>());
        String currentRole = role(auth);
        if ("MANAGER".equals(currentRole)) {
            Employee manager = currentEmployee(auth);
            Set<Long> directReportIds = employeeRepo.findByReportingManager_Id(manager.getId()).stream()
                    .map(Employee::getId).collect(Collectors.toSet());
            if (selected.stream().anyMatch(e -> !directReportIds.contains(e.getId()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Managers can only assign their direct reports to projects");
            }
            // A manager changes only assignments belonging to their own team;
            // assignments owned by other managers are preserved.
            List<Employee> preserved = p.getAssignedEmployees().stream()
                    .filter(e -> !directReportIds.contains(e.getId()))
                    .collect(Collectors.toCollection(ArrayList::new));
            preserved.addAll(selected);
            p.getAssignedEmployees().clear();
            p.getAssignedEmployees().addAll(preserved.stream().distinct().toList());
            p.setProjectManagerId(manager.getId());
            p.setProjectManagerName(manager.getFullName());
        } else {
            requireRoles(auth, "ADMIN", "HR");
            p.getAssignedEmployees().clear();
            p.getAssignedEmployees().addAll(selected);
            applyProjectManagerFromBody(p, body);
            if (p.getProjectManagerId() == null && !selected.isEmpty() && selected.get(0).getReportingManager() != null) {
                Employee manager = selected.get(0).getReportingManager();
                p.setProjectManagerId(manager.getId());
                p.setProjectManagerName(manager.getFullName());
            }
        }

        List<Employee> allAssigned = p.getAssignedEmployees();
        if (allAssigned.isEmpty()) {
            p.setAssignedEmployeeId(null); p.setAssignedEmployeeName(null); p.setEmployeeProfileId(null);
        } else {
            Employee primary = allAssigned.get(0);
            p.setAssignedEmployeeId(primary.getId());
            p.setAssignedEmployeeName(primary.getFullName());
            p.setEmployeeProfileId(primary.getEmployeeProfile() == null ? null : primary.getEmployeeProfile().getId());
        }
        p.setUpdatedAt(LocalDateTime.now());
        return projectMap(projectRepo.save(p));
    }

    @DeleteMapping("/projects/{id}")
    @Transactional
    public Map<String, Object> deleteProject(@PathVariable Long id) {
        Project p = projectRepo.findById(id).orElseThrow(() -> notFound("Project"));
        // Preserve project history and dependent timesheet records. DELETE is an archive operation.
        p.setStatus(ProjectStatus.INACTIVE); p.setUpdatedAt(LocalDateTime.now());
        projectRepo.save(p);
        return Map.of("success", true, "archived", true);
    }

    // ==================== SALARY ====================

    @GetMapping("/salaries/my")
    public List<Map<String, Object>> mySalaries(Authentication auth) {
        EmployeeProfile p = profileRepo.findByUserId(userId(auth)).orElse(null);
        return p == null ? List.of() : salaryRepo.findByEmployeeProfileId(p.getId()).stream().map(this::salaryMap).toList();
    }

    @GetMapping("/salaries")
    public List<Map<String, Object>> salaries(Authentication auth) {
        requireRoles(auth, "ADMIN", "HR");
        return salaryRepo.findAll().stream().map(this::salaryMap).toList();
    }

    /**
     * Read-only salary records for a Manager's direct reports. A Manager never
     * receives organization-wide payroll data from this endpoint.
     */
    @GetMapping("/salaries/team")
    public List<Map<String, Object>> teamSalaries(Authentication auth,
                                                  @RequestParam(required = false) String month,
                                                  @RequestParam(required = false) Integer year) {
        requireRoles(auth, "MANAGER");
        Employee manager = currentEmployee(auth);
        List<Salary> rows;
        if (month != null && !month.isBlank() && year != null) {
            rows = salaryRepo.findByReportingManagerIdAndMonthYear(manager.getId(), month, year);
        } else {
            rows = salaryRepo.findByReportingManagerId(manager.getId());
        }
        return rows.stream().map(this::salaryMap).toList();
    }

    @GetMapping("/salaries/{id}/payslip")
    public Map<String, Object> payslip(Authentication auth, @PathVariable Long id) {
        Salary salary = salaryRepo.findById(id).orElseThrow(() -> notFound("Salary"));
        String currentRole = role(auth);
        boolean management = "ADMIN".equals(currentRole) || "HR".equals(currentRole);
        boolean own = Objects.equals(userId(auth), salary.getEmployeeCode());
        boolean managerTeam = "MANAGER".equals(currentRole) && isDirectReport(auth, salary);
        if (!management && !own && !managerTeam) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only view your own payslip or a direct report's payslip");
        }
        return salaryMap(salary);
    }

    @GetMapping("/salary-structures")
    public List<SalaryStructureResponseDTO> salaryStructures(Authentication auth) {
        requireRoles(auth, "ADMIN", "HR");
        return salaryStructureService.getAllSalaryStructures().stream()
                .map(SalaryStructureResponseDTO::fromEntity)
                .toList();
    }

    @GetMapping("/salary-structures/{profileId}")
    public SalaryStructureResponseDTO salaryStructure(Authentication auth, @PathVariable Long profileId) {
        requireRoles(auth, "ADMIN", "HR");
        return SalaryStructureResponseDTO.fromEntity(
                salaryStructureService.getSalaryStructureByEmployeeProfileId(profileId)
        );
    }

    @PostMapping("/salary-structures")
    public SalaryStructureResponseDTO createSalaryStructure(Authentication auth, @RequestBody SalaryStructureDTO dto) {
        requireHr(auth);
        return SalaryStructureResponseDTO.fromEntity(salaryStructureService.saveSalaryStructure(dto));
    }

    @PutMapping("/salary-structures/{id}")
    public SalaryStructureResponseDTO updateSalaryStructure(Authentication auth, @PathVariable Long id, @RequestBody SalaryStructureDTO dto) {
        requireHr(auth);
        dto.setId(id);
        return SalaryStructureResponseDTO.fromEntity(salaryStructureService.updateSalaryStructure(dto));
    }

    @DeleteMapping("/salary-structures/{id}")
    public Map<String, Object> deleteSalaryStructure(Authentication auth, @PathVariable Long id) {
        requireHr(auth);
        salaryStructureService.deleteSalaryStructure(id);
        return Map.of("success", true);
    }

    @PostMapping("/salaries/generate")
    public Map<String, Object> generateSalaries(Authentication auth, @RequestBody Map<String, Object> body) {
        requireHr(auth);
        String month = text(body.get("month"));
        int year = intValue(body.get("year"), LocalDate.now().getYear());
        if (month.isBlank()) month = LocalDate.now().getMonth().name();
        if (year < 2000 || year > 2100) throw badException("Invalid payroll year");
        List<EmployeeProfile> active = profileRepo.findAll().stream().filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus())).toList();
        List<Map<String,Object>> generated = new java.util.ArrayList<>();
        List<Map<String,Object>> errors = new java.util.ArrayList<>();
        for (EmployeeProfile profile : active) {
            try {
                Salary salary = monthlySalaryService.generateMonthlySalary(profile.getId(), month, year);
                salary.setGeneratedBy(userId(auth));
                if (salary.getGeneratedAt() == null) salary.setGeneratedAt(LocalDateTime.now());
                salary = salaryRepo.save(salary);
                generated.add(salaryMap(salary));
            } catch (Exception ex) {
                errors.add(Map.of("employeeProfileId", profile.getId(), "employee", profile.getFullName(), "message", ex.getMessage() == null ? "Unable to generate" : ex.getMessage()));
            }
        }
        return Map.of("month", month, "year", year, "generated", generated.size(), "skipped", errors.size(), "records", generated, "errors", errors);
    }

    @PostMapping("/salaries/generate/{profileId}")
    public Map<String, Object> generateSalary(Authentication auth, @PathVariable Long profileId, @RequestBody Map<String, Object> body) {
        requireHr(auth);
        String month = text(body.get("month"));
        int year = intValue(body.get("year"), LocalDate.now().getYear());
        if (month.isBlank()) month = LocalDate.now().getMonth().name();
        Salary salary = monthlySalaryService.generateMonthlySalary(profileId, month, year);
        salary.setGeneratedBy(userId(auth));
        if (salary.getGeneratedAt() == null) salary.setGeneratedAt(LocalDateTime.now());
        return salaryMap(salaryRepo.save(salary));
    }

    @GetMapping("/salaries/manual")
    public List<Map<String, Object>> manualSalaries(Authentication auth) {
        requireRoles(auth, "ADMIN", "HR");
        return salaryRepo.findAll().stream().map(this::salaryMap).toList();
    }

    @PostMapping("/salaries/manual")
    @Transactional
    public Map<String, Object> createManualSalary(Authentication auth, @RequestBody Map<String, Object> body) {
        requireRoles(auth, "ADMIN", "HR");
        Salary salary = new Salary();
        applyManualSalary(salary, body);
        return salaryMap(salaryRepo.save(salary));
    }

    @PutMapping("/salaries/manual/{id}")
    @Transactional
    public Map<String, Object> updateManualSalary(Authentication auth, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireRoles(auth, "ADMIN", "HR");
        Salary salary = salaryRepo.findById(id).orElseThrow(() -> notFound("Salary"));
        applyManualSalary(salary, body);
        return salaryMap(salaryRepo.save(salary));
    }

    @DeleteMapping("/salaries/manual/{id}")
    @Transactional
    public Map<String, Object> deleteManualSalary(Authentication auth, @PathVariable Long id) {
        requireRoles(auth, "ADMIN", "HR");
        if (!salaryRepo.existsById(id)) throw notFound("Salary");
        salaryRepo.deleteById(id);
        return Map.of("success", true, "id", id);
    }

    private void applyManualSalary(Salary salary, Map<String, Object> body) {
        Long employeeId = longValue(body.get("employeeId"));
        Employee employee = employeeRepo.findById(employeeId).orElseThrow(() -> notFound("Employee"));
        salary.setEmployee(employee);
        salary.setEmployeeCode(employee.getRegisterEmployee() == null ? null : employee.getRegisterEmployee().getUserId());
        salary.setEmployeeName(employee.getFullName());
        salary.setDepartment(employee.getDepartment() == null ? null : employee.getDepartment().getDepartmentName());
        salary.setDesignation(employee.getRegisterEmployee() == null ? null : employee.getRegisterEmployee().getDesignation());
        salary.setBasicSalary(number(body.get("basicSalary")));
        salary.setHra(number(body.get("hra")));
        salary.setAllowance(number(body.get("allowance")));
        salary.setBonus(number(body.get("bonus")));
        salary.setDeductions(number(body.get("deductions")));
        salary.setGrossSalary((salary.getBasicSalary() == null ? 0 : salary.getBasicSalary()) + (salary.getHra() == null ? 0 : salary.getHra()) + (salary.getAllowance() == null ? 0 : salary.getAllowance()) + (salary.getBonus() == null ? 0 : salary.getBonus()));
        salary.setNetSalary(salary.getGrossSalary() - (salary.getDeductions() == null ? 0 : salary.getDeductions()));
        salary.setMonth(text(body.get("month")));
        salary.setYear(intValue(body.get("year"), LocalDate.now().getYear()));
        salary.setPaymentStatus(text(body.get("paymentStatus")).isBlank() ? "PENDING" : text(body.get("paymentStatus")).toUpperCase());
    }

    private Double number(Object value) {
        String s = text(value);
        return s.isBlank() ? 0d : Double.valueOf(s);
    }

    @PatchMapping("/salaries/{id}/payment-status")
    public Map<String, Object> updateSalaryPaymentStatus(Authentication auth, @PathVariable Long id, @RequestBody Map<String, String> body) {
        requireHr(auth);
        Salary salary = salaryRepo.findById(id).orElseThrow(() -> notFound("Salary"));
        String status = text(body.get("paymentStatus")).toUpperCase();
        if (status.isBlank()) bad("Payment status is required");
        if (!List.of("PENDING", "PROCESSING", "PAID", "FAILED").contains(status)) bad("Invalid payment status");
        salary.setPaymentStatus(status);
        if ("PAID".equals(status) && salary.getPaymentDate() == null) salary.setPaymentDate(LocalDate.now());
        if (!"PAID".equals(status)) salary.setPaymentDate(null);
        return salaryMap(salaryRepo.save(salary));
    }

    @GetMapping("/salaries/monthly")
    public List<Map<String, Object>> monthlySalaries(Authentication auth, @RequestParam String month, @RequestParam Integer year) {
        requireRoles(auth, "ADMIN", "HR");
        return monthlySalaryService.getSalariesByMonth(month, year).stream().map(this::salaryMap).toList();
    }

    // ==================== NOTIFICATIONS ====================

    @GetMapping(value = "/notifications/stream", produces = "text/event-stream")
    public SseEmitter notificationStream(Authentication auth) {
        return notificationSseService.subscribe(userId(auth));
    }

    @GetMapping("/notifications/my")
    public List<Map<String, Object>> notifications(Authentication auth) {
        return notificationRepo.findForUser(userId(auth)).stream()
                .map(this::notificationMap)
                .toList();
    }

    /**
     * Read-only diagnostics for the signed-in user. Does not disclose other
     * employees' notifications or security credentials.
     */
    @GetMapping("/notifications/diagnostics")
    public Map<String, Object> notificationDiagnostics(Authentication auth) {
        String uid = userId(auth);
        List<Notification> own = notificationRepo.findForUser(uid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("authenticatedUserId", uid);
        data.put("role", role(auth));
        data.put("totalForUser", own.size());
        data.put("unreadForUser", notificationRepo.countUnreadForUser(uid));
        data.put("linkedEmployee", findCurrentEmployee(auth).isPresent());
        return data;
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Object> unreadNotificationCount(Authentication auth) {
        // The unread badge always reflects the authenticated user's own inbox.
        // Admin-wide visibility must not merge everybody's unread counts.
        return Map.of("count", notificationRepo.countUnreadForUser(userId(auth)));
    }

    @GetMapping("/notifications")
    public List<Map<String, Object>> allNotifications(Authentication auth) {
        if (!"ADMIN".equals(role(auth))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Admin can view all employee notifications");
        }
        return notificationRepo.findAll().stream().sorted(Comparator.comparing(Notification::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(n -> adminNotificationMap(n)).toList();
    }

    /**
     * Admin-wide notifications describe the organization event, not the recipient's
     * personal inbox. Keep the stored message unchanged so the employee still sees
     * the natural "Your ..." wording in their own inbox.
     */
    private Map<String, Object> adminNotificationMap(Notification n) {
        Map<String, Object> m = notificationMap(n);
        String message = text(n.getMessage());
        String employeeName = n.getEmployee() == null ? "the employee" : text(n.getEmployee().getFullName());
        if (employeeName.isBlank()) employeeName = "the employee";
        m.put("message", adminFacingNotificationMessage(message, employeeName));
        return m;
    }

    private String adminFacingNotificationMessage(String message, String employeeName) {
        if (message.isBlank()) return message;

        // Example: "Your timesheet for 2026-09-28 was approved by Rahul Singh."
        java.util.regex.Matcher timesheet = java.util.regex.Pattern.compile(
                "(?i)^Your timesheet for (.+?) was (approved|rejected) by (.+?)(?:\\: (.*))?\\.?$").matcher(message);
        if (timesheet.matches()) {
            String date = timesheet.group(1).trim();
            String action = timesheet.group(2).toLowerCase(Locale.ENGLISH);
            String actor = timesheet.group(3).trim();
            String note = timesheet.group(4);
            String result = actor + " " + action + " " + employeeName + "'s timesheet for " + date;
            if (note != null && !note.isBlank()) result += ". Review note: " + note.trim();
            return result + ".";
        }

        // Example: "Your X leave request was approved by Rahul Singh."
        java.util.regex.Matcher approval = java.util.regex.Pattern.compile(
                "(?i)^Your (.+?) was (approved|rejected) by (.+?)\\.?$").matcher(message);
        if (approval.matches()) {
            String subject = approval.group(1).trim();
            String action = approval.group(2).toLowerCase(Locale.ENGLISH);
            String actor = approval.group(3).trim();
            return actor + " " + action + " " + employeeName + "'s " + subject + ".";
        }

        // Example: "Rahul Singh sent you birthday wishes: ..."
        java.util.regex.Matcher wish = java.util.regex.Pattern.compile(
                "(?i)^(?:🎉\\s*)?(.+?) sent you (birthday|anniversary) wishes:?\\s*(.*)$").matcher(message);
        if (wish.matches()) {
            String actor = wish.group(1).trim();
            String kind = wish.group(2).toLowerCase(Locale.ENGLISH);
            String detail = wish.group(3).trim();
            return actor + " sent " + kind + " wishes to " + employeeName + (detail.isBlank() ? "." : ": " + detail);
        }

        // Convert common personal-inbox wording to an organization-oriented message
        // only when the message clearly names an actor; otherwise retain original text.
        if (message.regionMatches(true, 0, "Your ", 0, 5) && message.contains(" by ")) {
            return message.replaceFirst("(?i)^Your ", employeeName + "'s ");
        }
        return message;
    }

    @PatchMapping("/notifications/read-all")
    @Transactional
    public Map<String, Object> markAllRead(Authentication auth) {
        // Read state belongs to the recipient, including when an Admin is signed in.
        List<Notification> notifications = notificationRepo.findForUser(userId(auth));
        int updated = 0;
        for (Notification n : notifications) {
            if (!n.isRead()) {
                n.setRead(true);
                updated++;
            }
        }
        notificationRepo.saveAll(notifications);
        return Map.of("success", true, "updated", updated);
    }

    @PatchMapping("/notifications/{id}/read")
    public Map<String, Object> markRead(Authentication auth, @PathVariable Long id) {
        Notification n = notificationRepo.findById(id).orElseThrow(() -> notFound("Notification"));
        String currentUserId = userId(auth);
        String recipientUserId = text(n.getRecipientUserId());
        if (recipientUserId.isBlank() && n.getEmployee() != null && n.getEmployee().getRegisterEmployee() != null) {
            recipientUserId = text(n.getEmployee().getRegisterEmployee().getUserId());
        }
        if (!currentUserId.equals(recipientUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only update your own notifications");
        }
        n.setRead(true);
        notificationRepo.save(n);
        return Map.of("success", true);
    }

    // ==================== PROFILE / SETTINGS ====================

    @GetMapping("/profile/me")
    public Map<String, Object> profile(Authentication auth) {
        String uid = userId(auth);
        Optional<EmployeeProfile> existing = profileRepo.findByUserId(uid);
        if (existing.isPresent()) return profileMap(existing.get());
        RegisterEmployee account = registerRepo.findByUserId(uid);
        if (account == null) throw notFound("Profile");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", account.getUserId());
        m.put("fullName", account.getName());
        m.put("email", account.getEmail());
        m.put("phoneNumber", account.getPhoneNumber());
        m.put("designation", account.getDesignation());
        m.put("role", normalizeRole(account.getRole()));
        return m;
    }

    @PutMapping("/profile/me")
    @Transactional
    public Map<String, Object> updateProfile(Authentication auth, @RequestBody Map<String, Object> body) {
        String uid = userId(auth);
        EmployeeProfile p = profileRepo.findByUserId(uid).orElse(null);
        RegisterEmployee account = registerRepo.findByUserId(uid);
        if (account == null) throw notFound("User account");

        if (p == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile is not provisioned for this account. Contact HR or an administrator.");
        } else {
            // Self-service profile editing is intentionally limited to personal information.
            // Role, status, department, designation and password are administrative fields.
            body.remove("id"); body.remove("userId"); body.remove("password"); body.remove("status");
            body.remove("role"); body.remove("department"); body.remove("designation");
            updatePersonalProfileFields(p, body);
        }

        account.setName(p.getFullName());
        account.setEmail(p.getEmail());
        account.setPhoneNumber(p.getPhoneNumber());
        account.setDesignation(p.getDesignation());
        registerRepo.save(account);
        if (p.getEmployee() != null) {
            p.getEmployee().setFullName(p.getFullName());
            p.getEmployee().setEmail(p.getEmail());
            p.getEmployee().setPhone(p.getPhoneNumber());
            p.getEmployee().setDateOfBirth(p.getDob());
            employeeRepo.save(p.getEmployee());
        }
        return profileMap(profileRepo.save(p));
    }

    // ==================== REPORTS ====================

    @GetMapping("/reports/employees")
    public List<EmployeeReportDTO> employeeReports(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Long departmentId,
                                                     @RequestParam(required = false) String status) {
        return employeeReportService.getEmployeeReports(keyword, departmentId, status, PageRequest.of(0, 1000)).getContent();
    }

    @GetMapping("/reports/employees/{id}")
    public EmployeeReportDTO employeeReport(@PathVariable Long id) { return employeeReportService.getEmployeeDetail(id); }

    @GetMapping("/reports/attendance")
    public List<Map<String, Object>> attendanceReport() { return allAttendance(); }

    @GetMapping("/reports/payroll")
    public List<Map<String, Object>> payrollReport(Authentication auth) { return salaries(auth); }

    @GetMapping("/reports/projects")
    public List<Map<String, Object>> projectReport(Authentication auth) { return projects(auth); }

    @GetMapping("/reports/leaves")
    public List<Map<String, Object>> leaveReport() {
        return leaveRepo.findAll().stream().map(this::leaveMap).toList();
    }

    @GetMapping("/employee-hierarchy")
    public Object employeeHierarchy() { return employeeHierarchyService.getEmployeeHierarchy(); }

    // ==================== HELPERS ====================

    private Optional<Employee> findCurrentEmployee(Authentication auth) {
        String uid = userId(auth);
        Optional<Employee> direct = employeeRepo.findByRegisterEmployee_UserId(uid);
        if (direct.isPresent()) return direct;
        return profileRepo.findByUserId(uid).map(EmployeeProfile::getEmployee).filter(Objects::nonNull);
    }

    private Employee currentEmployee(Authentication auth) {
        return findCurrentEmployee(auth).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee account is not linked to this user. Please ask an administrator to complete the employee profile."));
    }

    private boolean isDirectReport(Authentication auth, Salary salary) {
        Employee manager = currentEmployee(auth);
        Employee employee = salary.getEmployee();
        return employee != null && employee.getReportingManager() != null
                && Objects.equals(employee.getReportingManager().getId(), manager.getId());
    }

    private String userId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return auth.getName();
    }

    private String role(Authentication auth) {
        if (auth == null) return "";
        return auth.getAuthorities().stream().findFirst().map(a -> a.getAuthority().replace("ROLE_", "").toUpperCase()).orElse("");
    }

    private void requireHr(Authentication auth) {
        if (!"HR".equals(role(auth))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only HR can generate or modify payroll");
        }
    }

    private void requireRoles(Authentication auth, String... allowed) {
        String current = role(auth);
        if (Arrays.stream(allowed).noneMatch(current::equals)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to access this payroll resource");
        }
    }

    private String accountRole(String employeeUserId) {
        if (employeeUserId == null || employeeUserId.isBlank()) return "";
        RegisterEmployee target = registerRepo.findByUserId(employeeUserId);
        if (target == null || text(target.getRole()).isBlank()) return "";
        try { return normalizeRole(target.getRole()); }
        catch (ResponseStatusException invalidRole) { return ""; }
    }

    private boolean canReviewWorkflow(Authentication auth, String requesterId) {
        if (requesterId == null || requesterId.isBlank() || requesterId.equals(userId(auth))) return false;
        String reviewer = role(auth);
        boolean directReport = "MANAGER".equals(reviewer) &&
                employeeRepo.findByReportingManager_Id(currentEmployee(auth).getId()).stream()
                        .anyMatch(e -> e.getRegisterEmployee() != null
                                && requesterId.equals(e.getRegisterEmployee().getUserId()));
        return WorkflowApprovalPolicy.canReview(reviewer, accountRole(requesterId), directReport);
    }

    private void assertCanReviewWorkflow(Authentication auth, String requesterId) {
        if (!canReviewWorkflow(auth, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only Admin can approve HR leave/timesheets; Admin or HR can review Managers; " +
                    "only HR or the assigned Manager can review employee requests");
        }
    }

    private Map<String, Object> leaveReviewMap(Authentication auth, LeaveRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId()); m.put("employeeId", r.getEmployeeId()); m.put("employeeName", r.getEmployeeName());
        m.put("employeeRole", accountRole(r.getEmployeeId()));
        m.put("canReview", canReviewWorkflow(auth, r.getEmployeeId()));
        m.put("startDate", r.getStartDate()); m.put("endDate", r.getEndDate());
        m.put("leaveType", r.getLeaveType()); m.put("leaveDurationType", r.getLeaveDurationType());
        m.put("reason", r.getReason()); m.put("status", r.getStatus());
        m.put("approvalComment", r.getApprovalComment()); m.put("approvedAt", r.getApprovedAt());
        m.put("createdAt", r.getCreatedAt()); m.put("totalDays", r.getTotalDays());
        m.put("reportingManagerName", r.getReportingManagerName());
        m.put("reportingManagerEmail", r.getReportingManagerEmail());
        m.put("managerNotificationEmail", r.getManagerNotificationEmail());
        m.put("ccEmailTo", r.getCcEmailTo());
        return m;
    }

    private Map<String, Object> timesheetReviewMap(Authentication auth, Timesheet t) {
        Map<String, Object> m = timesheetMap(t);
        m.put("canReview", canReviewWorkflow(auth, t.getEmployeeId()));
        return m;
    }

    private void assertCanManageUser(Authentication auth, String employeeUserId) {
        String currentRole = role(auth);
        if ("ADMIN".equals(currentRole) || "HR".equals(currentRole)) return;
        if (!"MANAGER".equals(currentRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This action is not allowed for your role");
        }
        if (employeeUserId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee is not assigned");
        Set<String> teamIds = employeeRepo.findByReportingManager_Id(currentEmployee(auth).getId()).stream()
                .map(e -> e.getRegisterEmployee() == null ? null : e.getRegisterEmployee().getUserId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!teamIds.contains(employeeUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your direct reports");
        }
    }

    private String currentName(Authentication auth) {
        RegisterEmployee r = registerRepo.findByUserId(userId(auth));
        return r == null || text(r.getName()).isBlank() ? userId(auth) : r.getName();
    }

    private Optional<Employee> employeeForAccount(RegisterEmployee account) {
        if (account == null) return Optional.empty();
        if (account.getEmployee() != null) return Optional.of(account.getEmployee());
        if (!text(account.getUserId()).isBlank()) {
            Optional<Employee> direct = employeeRepo.findByRegisterEmployee_UserId(account.getUserId());
            if (direct.isPresent()) return direct;
            return profileRepo.findByUserId(account.getUserId())
                    .map(EmployeeProfile::getEmployee)
                    .filter(Objects::nonNull);
        }
        return Optional.empty();
    }

    private Optional<Employee> reportingManagerFor(Employee employee) {
        if (employee == null) return Optional.empty();
        if (employee.getReportingManager() != null) return Optional.of(employee.getReportingManager());
        RegisterEmployee account = employee.getRegisterEmployee();
        if (account != null && account.getReportingManagerId() != null) {
            return employeeRepo.findById(account.getReportingManagerId());
        }
        return Optional.empty();
    }

    private void saveNotificationForUser(String recipientUserId, String message) {
        String uid = text(recipientUserId).trim();
        if (uid.isBlank() || text(message).isBlank()) return;

        Notification n = new Notification();
        n.setRecipientUserId(uid);
        RegisterEmployee account = registerRepo.findByUserId(uid);
        employeeForAccount(account).ifPresent(n::setEmployee);
        // Existing databases may retain VARCHAR(255); store the full review reason
        // on the request and a safe summary on the notification.
        String preview = message.trim();
        n.setMessage(preview.length() > 240 ? preview.substring(0, 237) + "..." : preview);
        n.setCreatedAt(LocalDateTime.now());
        n.setRead(false);
        Notification saved = notificationRepo.save(n);
        notificationSseService.publish(saved);
    }

    private void saveNotification(Employee recipient, String message) {
        if (recipient == null || text(message).isBlank()) return;
        String uid = recipient.getRegisterEmployee() == null ? "" : text(recipient.getRegisterEmployee().getUserId());
        if (!uid.isBlank()) {
            saveNotificationForUser(uid, message);
            return;
        }
        Notification n = new Notification();
        n.setEmployee(recipient);
        // Existing databases may retain VARCHAR(255); store the full review reason
        // on the request and a safe summary on the notification.
        String preview = message.trim();
        n.setMessage(preview.length() > 240 ? preview.substring(0, 237) + "..." : preview);
        n.setCreatedAt(LocalDateTime.now());
        n.setRead(false);
        Notification saved = notificationRepo.save(n);
        notificationSseService.publish(saved);
    }

    private void notifyUser(String employeeUserId, String message) {
        if (text(employeeUserId).isBlank()) return;
        saveNotificationForUser(employeeUserId, message);
    }

    private void notifyReportingManagerOf(String employeeUserId, String message) {
        if (text(employeeUserId).isBlank()) return;
        employeeRepo.findByRegisterEmployee_UserId(employeeUserId)
                .flatMap(this::reportingManagerFor)
                .map(Employee::getRegisterEmployee)
                .map(RegisterEmployee::getUserId)
                .filter(uid -> !text(uid).isBlank())
                .ifPresent(uid -> saveNotificationForUser(uid, message));
    }

    /**
     * Sends the workflow event to the submitter's reporting Manager and every
     * HR login.  User-id based delivery makes this reliable for both newly
     * created employees and older HR/Manager accounts whose inverse Employee
     * relationship may not have been populated historically.
     */
    private void notifyReviewers(Authentication auth, String activity, String details) {
        Employee actor = currentEmployee(auth);
        String actorUserId = userId(auth);
        String actorName = currentName(auth);
        String message = actorName + " (" + actorUserId + ") submitted " + activity
                + (text(details).isBlank() ? "." : " · " + details + ".") + " Review required.";

        Set<String> recipientUserIds = new LinkedHashSet<>();
        if (text(activity).toLowerCase(Locale.ENGLISH).startsWith("timesheet")) {
            String actorRole = role(auth);
            if ("EMPLOYEE".equals(actorRole)) {
                reportingManagerFor(actor).map(Employee::getRegisterEmployee)
                        .map(RegisterEmployee::getUserId).filter(uid -> !text(uid).isBlank())
                        .ifPresent(recipientUserIds::add);
            }
            for (RegisterEmployee account : registerRepo.findAll()) {
                String recipientRole = accountRole(account.getUserId());
                if (LeaveWorkflowRules.notifyRole(actorRole, recipientRole)
                        && !("EMPLOYEE".equals(actorRole) && "MANAGER".equals(recipientRole))) {
                    String uid = text(account.getUserId()).trim();
                    if (!uid.isBlank()) recipientUserIds.add(uid);
                }
            }
        } else {
            // Preserve the existing WFH workflow separately from timesheet approvals.
            reportingManagerFor(actor).map(Employee::getRegisterEmployee)
                    .map(RegisterEmployee::getUserId).filter(uid -> !text(uid).isBlank())
                    .ifPresent(recipientUserIds::add);
            for (RegisterEmployee account : registerRepo.findAll()) {
                if (!"HR".equals(normalizeRole(account.getRole()))) continue;
                String uid = text(account.getUserId()).trim();
                if (!uid.isBlank()) recipientUserIds.add(uid);
            }
        }
        recipientUserIds.remove(actorUserId);
        recipientUserIds.forEach(uid -> saveNotificationForUser(uid, message));
    }

    /** Employee -> assigned manager + HR; Manager -> Admin + HR; HR -> Admin. */
    private void notifyLeaveReviewers(Authentication auth, LeaveRequest leave) {
        String actorRole = role(auth);
        String message = leave.getEmployeeName() + " (" + leave.getEmployeeId() + ") requested "
                + leave.getLeaveType() + " / " + leave.getLeaveDurationType() + " from "
                + leave.getStartDate() + " to " + leave.getEndDate() + ". Review required.";
        Set<String> recipients = new LinkedHashSet<>();
        if ("EMPLOYEE".equals(actorRole)) {
            reportingManagerFor(currentEmployee(auth)).map(Employee::getRegisterEmployee)
                    .map(RegisterEmployee::getUserId).filter(id -> !text(id).isBlank())
                    .ifPresent(recipients::add);
        }
        for (RegisterEmployee account : registerRepo.findAll()) {
            String recipientRole = normalizeRole(account.getRole());
            if (LeaveWorkflowRules.notifyRole(actorRole, recipientRole)
                    && !("EMPLOYEE".equals(actorRole) && "MANAGER".equals(recipientRole))) {
                // Employee manager notifications must go only to the assigned manager.
                if (!text(account.getUserId()).isBlank()) recipients.add(account.getUserId());
            }
        }
        recipients.remove(userId(auth));
        recipients.forEach(uid -> saveNotificationForUser(uid, message));
    }

    private void sendLeaveEmail(LeaveRequest leave) {
        String email = text(leave.getManagerNotificationEmail()).trim();
        if (email.isBlank()) return;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Bluethink HRMS - Leave request " + leave.getId());
            message.setText("A leave request requires review in Bluethink HRMS.\nEmployee: "
                    + leave.getEmployeeName() + " (" + leave.getEmployeeId() + ")\nCategory: "
                    + leave.getLeaveType() + "\nDuration: " + leave.getLeaveDurationType()
                    + "\nFrom: " + leave.getStartDate() + "\nTo: " + leave.getEndDate()
                    + "\n\nPlease sign in to approve or reject; replying to this email does not update the request.");
            mailSender.send(message);
        } catch (Exception ex) {
            System.err.println("Leave email delivery failed for request " + leave.getId() + ": " + ex.getClass().getSimpleName());
        }
    }

    private String generateTemporaryPassword(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) out.append(chars.charAt(random.nextInt(chars.length())));
        return out.toString();
    }

    private String nextUserId() {
        Optional<RegisterEmployee> last = registerRepo.findFirstByUserIdIsNotNullOrderByIdDesc();
        int next = 1;
        if (last.isPresent()) {
            String digits = text(last.get().getUserId()).replaceAll("[^0-9]", "");
            if (!digits.isBlank()) {
                try { next = Integer.parseInt(digits) + 1; } catch (NumberFormatException ignored) { }
            }
        }
        String candidate;
        do { candidate = String.format("EMP%04d", next++); }
        while (registerRepo.findByUserId(candidate) != null || profileRepo.existsByUserId(candidate));
        return candidate;
    }

    private String normalizeRole(String value) {
        String normalized = text(value)
                .replaceFirst("^ROLE_", "")
                .replace('-', ' ')
                .replace('_', ' ')
                .trim()
                .toUpperCase();

        if (normalized.isBlank()) return "EMPLOYEE";
        if (normalized.contains("ADMIN")) return "ADMIN";
        if (normalized.contains("HR")) return "HR";
        if (normalized.contains("MANAGER")) return "MANAGER";
        if (normalized.contains("EMPLOYEE")) return "EMPLOYEE";

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role. Allowed roles: ADMIN, HR, MANAGER, EMPLOYEE");
    }

    private Department resolveDepartment(String name) {
        if (name == null || name.isBlank()) return null;
        return departmentRepo.findByDepartmentName(name).orElse(null);
    }

    private void updatePersonalProfileFields(EmployeeProfile p, Map<String, Object> body) {
        String fullName = text(body.get("fullName"));
        String email = text(body.get("email"));
        if (!fullName.isBlank()) p.setFullName(fullName);
        if (!email.isBlank() && !email.equalsIgnoreCase(p.getEmail())) {
            if (profileRepo.findByEmail(email).filter(x -> !x.getId().equals(p.getId())).isPresent()
                    || registerRepo.findByEmail(email).filter(x -> !x.getUserId().equals(p.getUserId())).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
            }
            p.setEmail(email);
        }
        if (body.containsKey("phoneNumber")) p.setPhoneNumber(textOrNull(body.get("phoneNumber")));
        if (body.containsKey("alternatePhoneNumber")) p.setAlternatePhoneNumber(textOrNull(body.get("alternatePhoneNumber")));
        if (body.containsKey("personalEmail")) p.setPersonalEmail(textOrNull(body.get("personalEmail")));
        if (body.containsKey("dob")) p.setDob(parseOptionalDate(body.get("dob")));
        if (body.containsKey("gender")) p.setGender(upperTextOrNull(body.get("gender")));
        if (body.containsKey("bloodGroup")) p.setBloodGroup(upperTextOrNull(body.get("bloodGroup")));
        if (body.containsKey("maritalStatus")) p.setMaritalStatus(upperTextOrNull(body.get("maritalStatus")));
        if (body.containsKey("panNumber")) p.setPanNumber(textOrNull(body.get("panNumber")));
        if (body.containsKey("currentAddress")) p.setCurrentAddress(textOrNull(body.get("currentAddress")));
        if (body.containsKey("currentCity")) p.setCurrentCity(textOrNull(body.get("currentCity")));
        if (body.containsKey("currentState")) p.setCurrentState(textOrNull(body.get("currentState")));
        if (body.containsKey("currentPincode")) p.setCurrentPincode(textOrNull(body.get("currentPincode")));
        if (body.containsKey("permanentAddress")) p.setPermanentAddress(textOrNull(body.get("permanentAddress")));
        if (body.containsKey("permanentCity")) p.setPermanentCity(textOrNull(body.get("permanentCity")));
        if (body.containsKey("permanentState")) p.setPermanentState(textOrNull(body.get("permanentState")));
        if (body.containsKey("permanentPincode")) p.setPermanentPincode(textOrNull(body.get("permanentPincode")));
        if (body.containsKey("bankName")) p.setBankName(textOrNull(body.get("bankName")));
        if (body.containsKey("bankBranch")) p.setBankBranch(textOrNull(body.get("bankBranch")));
        if (body.containsKey("bankAccountType")) p.setBankAccountType(textOrNull(body.get("bankAccountType")));
        if (body.containsKey("accountHolderName")) p.setAccountHolderName(textOrNull(body.get("accountHolderName")));
        if (body.containsKey("accountNumber")) p.setAccountNumber(textOrNull(body.get("accountNumber")));
        if (body.containsKey("ifscCode")) p.setIfscCode(textOrNull(body.get("ifscCode")));
    }

    private void updateProfileFields(EmployeeProfile p, Map<String, Object> body) {
        String fullName = text(body.get("fullName"));
        String email = text(body.get("email"));
        if (!fullName.isBlank()) p.setFullName(fullName);
        if (!email.isBlank()) p.setEmail(email);
        if (body.containsKey("phoneNumber")) p.setPhoneNumber(textOrNull(body.get("phoneNumber")));
        if (body.containsKey("alternatePhoneNumber")) p.setAlternatePhoneNumber(textOrNull(body.get("alternatePhoneNumber")));
        if (body.containsKey("personalEmail")) p.setPersonalEmail(textOrNull(body.get("personalEmail")));
        if (body.containsKey("dob")) p.setDob(parseOptionalDate(body.get("dob")));
        if (body.containsKey("gender")) p.setGender(upperTextOrNull(body.get("gender")));
        if (body.containsKey("bloodGroup")) p.setBloodGroup(upperTextOrNull(body.get("bloodGroup")));
        if (body.containsKey("maritalStatus")) p.setMaritalStatus(upperTextOrNull(body.get("maritalStatus")));
        if (body.containsKey("department")) p.setDepartment(textOrNull(body.get("department")));
        if (body.containsKey("designation")) p.setDesignation(textOrNull(body.get("designation")));
        if (body.containsKey("employeeBand")) p.setEmployeeBand(upperTextOrNull(body.get("employeeBand")));
        if (body.containsKey("panNumber")) p.setPanNumber(textOrNull(body.get("panNumber")));
        if (body.containsKey("currentAddress")) p.setCurrentAddress(textOrNull(body.get("currentAddress")));
        if (body.containsKey("currentCity")) p.setCurrentCity(textOrNull(body.get("currentCity")));
        if (body.containsKey("currentState")) p.setCurrentState(textOrNull(body.get("currentState")));
        if (body.containsKey("currentPincode")) p.setCurrentPincode(textOrNull(body.get("currentPincode")));
        if (body.containsKey("permanentAddress")) p.setPermanentAddress(textOrNull(body.get("permanentAddress")));
        if (body.containsKey("permanentCity")) p.setPermanentCity(textOrNull(body.get("permanentCity")));
        if (body.containsKey("permanentState")) p.setPermanentState(textOrNull(body.get("permanentState")));
        if (body.containsKey("permanentPincode")) p.setPermanentPincode(textOrNull(body.get("permanentPincode")));
        if (body.containsKey("bankName")) p.setBankName(textOrNull(body.get("bankName")));
        if (body.containsKey("bankBranch")) p.setBankBranch(textOrNull(body.get("bankBranch")));
        if (body.containsKey("bankAccountType")) p.setBankAccountType(textOrNull(body.get("bankAccountType")));
        if (body.containsKey("accountHolderName")) p.setAccountHolderName(textOrNull(body.get("accountHolderName")));
        if (body.containsKey("accountNumber")) p.setAccountNumber(textOrNull(body.get("accountNumber")));
        if (body.containsKey("ifscCode")) p.setIfscCode(textOrNull(body.get("ifscCode")));
        if (body.containsKey("status") && !text(body.get("status")).isBlank()) p.setStatus(text(body.get("status")).toUpperCase());
    }

    private LocalDate parseDate(Object value, String label) {
        LocalDate date = parseOptionalDate(value);
        if (date == null) bad(label + " is required and must use YYYY-MM-DD");
        return date;
    }

    private LocalDate parseOptionalDate(Object value) {
        if (value == null || text(value).isBlank()) return null;
        try { return LocalDate.parse(text(value)); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date: " + value); }
    }

    private Long longValue(Object value) {
        if (value == null || text(value).isBlank()) return null;
        try { return Long.valueOf(text(value)); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid numeric ID"); }
    }

    private int intValue(Object value, int fallback) {
        if (value == null || text(value).isBlank()) return fallback;
        try { return Integer.parseInt(text(value)); }
        catch (Exception e) { return fallback; }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String textOrNull(Object value) { String s = text(value); return s.isBlank() ? null : s; }
    private String upperTextOrNull(Object value) { String s = text(value); return s.isBlank() ? null : s.toUpperCase(Locale.ENGLISH).replace(' ', '_'); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message + " not found"); }

    private void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException badException(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private String[] splitName(String fullName) {
        String[] parts = fullName.trim().split("\\s+", 2);
        return new String[]{parts[0], parts.length > 1 ? parts[1] : ""};
    }

    private WorkMode parseWorkMode(String value) {
        try { return WorkMode.valueOf(value == null || value.isBlank() ? "OFFICE" : value.toUpperCase()); }
        catch (Exception e) { return WorkMode.OFFICE; }
    }

    private Map<String, Object> profileMap(EmployeeProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("userId", p.getUserId());
        m.put("fullName", p.getFullName());
        m.put("email", p.getEmail());
        m.put("phoneNumber", p.getPhoneNumber());
        m.put("alternatePhoneNumber", p.getAlternatePhoneNumber());
        m.put("personalEmail", p.getPersonalEmail());
        m.put("dob", p.getDob());
        m.put("gender", p.getGender());
        m.put("bloodGroup", p.getBloodGroup());
        m.put("maritalStatus", p.getMaritalStatus());
        m.put("department", p.getDepartment());
        m.put("designation", p.getDesignation());
        m.put("employeeBand", p.getEmployeeBand());
        m.put("panNumber", p.getPanNumber());
        m.put("status", p.getStatus());
        m.put("currentAddress", p.getCurrentAddress());
        m.put("currentCity", p.getCurrentCity());
        m.put("currentState", p.getCurrentState());
        m.put("currentPincode", p.getCurrentPincode());
        m.put("permanentAddress", p.getPermanentAddress());
        m.put("permanentCity", p.getPermanentCity());
        m.put("permanentState", p.getPermanentState());
        m.put("permanentPincode", p.getPermanentPincode());
        m.put("bankName", p.getBankName());
        m.put("bankBranch", p.getBankBranch());
        m.put("bankAccountType", p.getBankAccountType());
        m.put("accountHolderName", p.getAccountHolderName());
        m.put("accountNumber", p.getAccountNumber());
        m.put("ifscCode", p.getIfscCode());
        m.put("highestQualification", p.getHighestQualification());
        m.put("fieldOfStudy", p.getFieldOfStudy());
        m.put("university", p.getUniversity());
        m.put("passingYear", p.getPassingYear());
        m.put("photoAvailable", p.getPhoto() != null && !p.getPhoto().isBlank());
        Employee e = p.getEmployee();
        m.put("employeeId", e == null ? null : e.getId());
        m.put("joiningDate", e == null ? null : e.getJoiningDate());
        m.put("workMode", e == null ? null : e.getWorkMode());
        m.put("reportingManagerId", e == null || e.getReportingManager() == null ? null : e.getReportingManager().getId());
        m.put("reportingManagerName", e == null || e.getReportingManager() == null ? null : e.getReportingManager().getFullName());
        m.put("role", e == null || e.getRegisterEmployee() == null ? "EMPLOYEE" : normalizeRole(e.getRegisterEmployee().getRole()));
        return m;
    }

    private Map<String, Object> employeeMap(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("employeeProfileId", e.getEmployeeProfile() == null ? null : e.getEmployeeProfile().getId());
        m.put("userId", e.getRegisterEmployee() == null ? null : e.getRegisterEmployee().getUserId());
        m.put("fullName", e.getFullName());
        m.put("email", e.getEmail());
        m.put("department", e.getDepartment() == null ? null : e.getDepartment().getDepartmentName());
        m.put("designation", e.getRegisterEmployee() == null ? null : e.getRegisterEmployee().getDesignation());
        m.put("role", e.getRegisterEmployee() == null ? null : normalizeRole(e.getRegisterEmployee().getRole()));
        return m;
    }

    private Map<String, Object> attendanceMap(Attendance a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId()); m.put("date", a.getDate()); m.put("checkInTime", a.getCheckInTime());
        m.put("checkOutTime", a.getCheckOutTime()); m.put("workingHours", a.getWorkingHours());
        m.put("status", a.getStatus()); m.put("workMode", a.getWorkMode());
        m.put("regularized", a.getRegularizedByRequestId() != null);
        m.put("regularizedByRequestId", a.getRegularizedByRequestId());
        m.put("regularizedAt", a.getRegularizedAt());
        m.put("sessionExpired", a.getCheckInTime() != null && a.getCheckOutTime() == null
                && a.getWorkingHours() != null && a.getWorkingHours() == 0.0);
        if (a.getEmployee() != null) {
            m.put("employeeId", a.getEmployee().getId());
            m.put("employeeName", a.getEmployee().getFullName());
            m.put("employeeEmail", a.getEmployee().getEmail());
            m.put("department", a.getEmployee().getDepartment() == null ? null : a.getEmployee().getDepartment().getDepartmentName());
        }
        return m;
    }

    private Map<String, Object> timesheetMap(Timesheet t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId()); m.put("employeeId", t.getEmployeeId()); m.put("employeeName", t.getEmployeeName());
        m.put("employeeRole", accountRole(t.getEmployeeId()));
        m.put("date", t.getDate()); m.put("hoursWorked", t.getHoursWorked()); m.put("taskDescription", t.getTaskDescription());
        m.put("workMode", t.getWorkMode()); m.put("status", t.getStatus()); m.put("approvalComment", t.getApprovalComment()); m.put("projectName", t.getProjectName());
        m.put("projectId", t.getProject() == null ? null : t.getProject().getId());
        MentorshipAssignment learning = t.getMentorshipAssignment();
        m.put("mentorshipId", learning == null ? null : learning.getId());
        m.put("mentorName", learning == null || learning.getMentor() == null ? null : learning.getMentor().getFullName());
        m.put("mentorUserId", learning == null || learning.getMentor() == null || learning.getMentor().getRegisterEmployee() == null ? null : learning.getMentor().getRegisterEmployee().getUserId());
        m.put("learningManagerName", learning == null || learning.getManager() == null ? null : learning.getManager().getFullName());
        m.put("assignmentType", learning == null ? null : learning.getAssignmentType());
        m.put("learningGoal", learning == null ? null : learning.getLearningGoal());
        m.put("mentorReviewStatus", t.getMentorReviewStatus());
        m.put("mentorReviewComment", t.getMentorReviewComment());
        m.put("mentorReviewedBy", t.getMentorReviewedBy());
        m.put("mentorReviewedAt", t.getMentorReviewedAt());
        m.put("managerReviewedBy", t.getManagerReviewedBy());
        m.put("managerReviewedAt", t.getManagerReviewedAt());
        return m;
    }

    private void attachTimesheetLearningAndProject(Authentication auth, Timesheet t, Map<String, Object> body) {
        boolean learningSpecified = body.containsKey("mentorshipId");
        Long mentorshipId = learningSpecified ? longValue(body.get("mentorshipId"))
                : (t.getMentorshipAssignment() == null ? null : t.getMentorshipAssignment().getId());
        if (mentorshipId != null) {
            MentorshipAssignment learning = mentorshipRepo.findById(mentorshipId)
                    .orElseThrow(() -> badException("Learning/shadow assignment not found"));
            Employee current = currentEmployee(auth);
            if (learning.getMentee() == null || !Objects.equals(learning.getMentee().getId(), current.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only log learning time for your own shadow assignment");
            }
            if (!"ACTIVE".equalsIgnoreCase(text(learning.getStatus()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This learning/shadow assignment is not active");
            }
            LocalDate date = t.getDate();
            if (date != null && learning.getStartDate() != null && date.isBefore(learning.getStartDate())) {
                bad("Timesheet date is before the learning assignment start date");
            }
            if (date != null && learning.getEndDate() != null && date.isAfter(learning.getEndDate())) {
                bad("Timesheet date is after the learning assignment end date");
            }
            t.setMentorshipAssignment(learning);
            t.setMentorReviewStatus("PENDING");
            t.setMentorReviewComment(null);
            t.setMentorReviewedBy(null);
            t.setMentorReviewedAt(null);
            if (learning.getProject() != null) {
                Object requested = body.get("projectId");
                if (requested != null && !text(requested).isBlank() && !Objects.equals(longValue(requested), learning.getProject().getId())) {
                    bad("This shadow assignment must be logged against " + learning.getProject().getProjectName());
                }
                t.setProject(learning.getProject());
                t.setProjectName(learning.getProject().getProjectName());
            } else {
                attachTimesheetProject(auth, t, body);
            }
            return;
        }

        t.setMentorshipAssignment(null);
        t.setMentorReviewStatus("NOT_REQUIRED");
        t.setMentorReviewComment(null);
        t.setMentorReviewedBy(null);
        t.setMentorReviewedAt(null);
        attachTimesheetProject(auth, t, body);
    }

    private void attachTimesheetProject(Authentication auth, Timesheet t, Map<String, Object> body) {
        Object projectId = body.get("projectId");
        if (projectId != null && !text(projectId).isBlank()) {
            Long id = longValue(projectId);
            Project project = projectRepo.findById(id).orElseThrow(() -> badException("Project not found"));
            validateTimesheetProjectAccess(auth, project);
            t.setProject(project);
            t.setProjectName(project.getProjectName());
            return;
        }
        String projectName = text(body.get("projectName"));
        if (!projectName.isBlank()) {
            Project project = projectRepo.findAll().stream()
                    .filter(p -> projectName.equalsIgnoreCase(text(p.getProjectName())))
                    .findFirst().orElse(null);
            if (project != null) { t.setProject(project); t.setProjectName(project.getProjectName()); }
            else t.setProjectName(projectName);
        } else {
            t.setProject(null); t.setProjectName(null);
        }
    }

    private void validateTimesheetProjectAccess(Authentication auth, Project project) {
        // Selecting a project is optional. When one is selected, HR, Manager and
        // Employee users must actually be assigned to it. This prevents someone
        // from logging work against an unrelated project just because it exists
        // in the project catalogue.
        if ("ADMIN".equals(role(auth))) return;
        Employee current = findCurrentEmployee(auth).orElse(null);
        boolean projectManager = current != null && Objects.equals(project.getProjectManagerId(), current.getId());
        boolean assigned = current != null && project.getAssignedEmployees() != null
                && project.getAssignedEmployees().stream().anyMatch(e -> Objects.equals(e.getId(), current.getId()));
        boolean learningProject = current != null && mentorshipRepo
                .findByMentee_IdAndStatusIgnoreCaseOrderByCreatedAtDesc(current.getId(), "ACTIVE").stream()
                .anyMatch(a -> a.getProject() != null && Objects.equals(a.getProject().getId(), project.getId()));
        if (!projectManager && !assigned && !learningProject) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only log time against projects assigned to you, managed by you, or linked to your active learning/shadow assignment");
        }
    }

    private void validateTimesheetEntryDate(LocalDate date) {
        if (date == null) bad("Timesheet date is required");
        LocalDate today = LocalDate.now();
        if (date.isAfter(today)) bad("Timesheet cannot be filled for a future date");
        LocalDate cutoff = YearMonth.from(date).plusMonths(1).atDay(2);
        if (!today.isBefore(cutoff)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Timesheet cutoff has passed. " + date.getMonth() + " " + date.getYear()
                            + " entries must be completed before " + cutoff + " so payroll can be finalized on the 2nd.");
        }
    }

    private boolean isImportantHoliday(Holiday holiday) {
        if (holiday == null || Boolean.FALSE.equals(holiday.getActive()) || Boolean.TRUE.equals(holiday.getOptionalHoliday())) return false;
        String type = text(holiday.getHolidayType()).toUpperCase(Locale.ENGLISH);
        return type.isBlank() || "PUBLIC".equals(type) || "COMPANY".equals(type);
    }

    private boolean isUnpaidLeave(String leaveType) {
        String type = text(leaveType).toUpperCase(Locale.ENGLISH);
        return type.contains("LEAVE WITHOUT PAY") || type.contains("LWP") || type.contains("UNPAID")
                || type.contains("LOSS OF PAY") || type.contains("LOSS-OF-PAY");
    }

    private void applyProjectManagerFromBody(Project project, Map<String, Object> body) {
        if (!body.containsKey("projectManagerId")) return;
        Long managerId = longValue(body.get("projectManagerId"));
        if (managerId == null) {
            project.setProjectManagerId(null);
            project.setProjectManagerName(null);
            return;
        }
        Employee manager = employeeRepo.findById(managerId).orElseThrow(() -> badException("Project manager not found"));
        RegisterEmployee account = manager.getRegisterEmployee();
        String managerRole = account == null ? "" : normalizeRole(account.getRole());
        if (!"MANAGER".equals(managerRole)) {
            throw badException("Project Manager must have the MANAGER role");
        }
        project.setProjectManagerId(manager.getId());
        project.setProjectManagerName(manager.getFullName());
    }

    private Map<String, Object> projectMap(Project p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId()); m.put("projectName", p.getProjectName()); m.put("clientName", p.getClientName());
        m.put("clientRegion", p.getClientRegion()); m.put("technology", p.getTechnology()); m.put("projectType", p.getProjectType());
        m.put("totalCost", p.getTotalCost()); m.put("onboardingDate", p.getOnboardingDate()); m.put("endDate", p.getEndDate());
        m.put("status", p.getStatus()); m.put("assignedEmployeeName", p.getAssignedEmployeeName());
        m.put("departmentId", p.getDepartmentId()); m.put("employeeProfileId", p.getEmployeeProfileId()); m.put("description", p.getDescription());
        m.put("projectManagerId", p.getProjectManagerId()); m.put("projectManagerName", p.getProjectManagerName());
        List<Map<String, Object>> assigned = p.getAssignedEmployees() == null ? List.of() : p.getAssignedEmployees().stream().map(e -> {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", e.getId());
            a.put("fullName", e.getFullName());
            a.put("userId", e.getRegisterEmployee() == null ? null : e.getRegisterEmployee().getUserId());
            return a;
        }).toList();
        m.put("assignedEmployees", assigned);
        return m;
    }

    private Map<String, Object> salaryMap(Salary s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("employeeCode", s.getEmployeeCode());
        m.put("employeeName", s.getEmployeeName());
        m.put("department", s.getDepartment());
        m.put("designation", s.getDesignation());
        m.put("employeeBand", s.getEmployeeBand());
        m.put("panNumber", s.getPanNumber());
        m.put("joiningDate", s.getJoiningDate());
        m.put("bankName", s.getBankName());
        m.put("bankAccountNumber", s.getBankAccountNumber());
        m.put("daysWorked", s.getDaysWorked());
        m.put("lwpDays", s.getLwpDays());
        m.put("scheduledWorkingDays", s.getScheduledWorkingDays());
        m.put("timesheetPresentDays", s.getTimesheetPresentDays());
        m.put("paidLeaveDays", s.getPaidLeaveDays());
        m.put("projectOffDays", s.getProjectOffDays());
        m.put("absentDays", s.getAbsentDays());
        m.put("payableDays", s.getPayableDays());
        m.put("attendanceDeduction", s.getAttendanceDeduction());
        m.put("timesheetCutoffDate", s.getTimesheetCutoffDate());

        m.put("basicSalary", s.getBasicSalary());
        m.put("hra", s.getHra());
        m.put("conveyance", s.getConveyance());
        m.put("medicalAllowance", s.getMedicalAllowance());
        m.put("specialAllowance", s.getSpecialAllowance());
        m.put("otherAllowance", s.getOtherAllowance());
        m.put("allowance", s.getAllowance());
        m.put("bonus", s.getBonus());
        m.put("grossSalary", s.getGrossSalary());

        m.put("pf", s.getPf());
        m.put("esi", s.getEsi());
        m.put("professionalTax", s.getProfessionalTax());
        m.put("tds", s.getTds());
        m.put("loanDeduction", s.getLoanDeduction());
        m.put("deductions", s.getDeductions());
        m.put("netSalary", s.getNetSalary());

        m.put("paymentStatus", s.getPaymentStatus());
        m.put("month", s.getMonth());
        m.put("year", s.getYear());
        m.put("paymentDate", s.getPaymentDate());
        m.put("generatedBy", s.getGeneratedBy());
        m.put("generatedAt", s.getGeneratedAt());
        m.put("employeeProfileId", s.getEmployeeProfileId());
        m.put("employeeId", s.getEmployee() == null ? null : s.getEmployee().getId());
        return m;
    }

    private Map<String, Object> leaveMap(LeaveRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId()); m.put("employeeId", r.getEmployeeId()); m.put("employeeName", r.getEmployeeName());
        m.put("leaveType", r.getLeaveType()); m.put("startDate", r.getStartDate()); m.put("endDate", r.getEndDate());
        m.put("totalDays", r.getStartDate() == null || r.getEndDate() == null ? 0 : r.getTotalDays());
        m.put("reason", r.getReason()); m.put("status", r.getStatus()); m.put("approvalComment", r.getApprovalComment());
        m.put("createdAt", r.getCreatedAt()); m.put("approvedAt", r.getApprovedAt());
        return m;
    }

    private Map<String, Object> notificationMap(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId()); m.put("message", n.getMessage()); m.put("createdAt", n.getCreatedAt()); m.put("read", n.isRead());
        m.put("recipientUserId", n.getRecipientUserId());
        if (n.getEmployee() != null) {
            m.put("employeeId", n.getEmployee().getId());
            m.put("employeeName", n.getEmployee().getFullName());
        }
        return m;
    }
}
