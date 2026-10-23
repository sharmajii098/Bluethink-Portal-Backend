package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.dto.ChangePasswordRequest;
import EmployeeManagementSystem.dto.LoginRequest;
import EmployeeManagementSystem.dto.LoginResponse;
import EmployeeManagementSystem.dto.UserSessionDTO;
import EmployeeManagementSystem.entity.Activity;
import EmployeeManagementSystem.entity.AttendanceTracking;
import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.entity.LoginLocation;
import EmployeeManagementSystem.entity.RegisterEmployee;
import EmployeeManagementSystem.entity.UserSession;
import EmployeeManagementSystem.repository.ActivityRepository;
import EmployeeManagementSystem.repository.AttendanceTrackingRepository;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.repository.UserSessionRepository;
import EmployeeManagementSystem.service.GeofenceService;
import EmployeeManagementSystem.service.LoginLocationService;
import EmployeeManagementSystem.service.NotificationService;
import EmployeeManagementSystem.service.RegisterEmployeeService;
import EmployeeManagementSystem.service.ExpiredWorkSessionService;
import EmployeeManagementSystem.service.WorkSessionRules;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRestController {

    private final RegisterEmployeeService registerEmployeeService;
    private final UserSessionRepository userSessionRepository;
    private final AttendanceTrackingRepository attendanceTrackingRepository;
    private final ExpiredWorkSessionService expiredWorkSessionService;
    private final EmployeeRepository employeeRepository;
    private final ActivityRepository activityRepository;
    private final PasswordEncoder passwordEncoder;
    private final GeofenceService geofenceService;
    private final LoginLocationService loginLocationService;
    private final NotificationService notificationService;


    // ============================================================
    // LOGIN
    // ============================================================

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request) {

        try {

            // ----------------------------------------------------
            // VALIDATION
            // ----------------------------------------------------

            if (request == null) {
                return ResponseEntity
                        .badRequest()
                        .body(errorResponse(
                                "Login request is required."
                        ));
            }

            if (request.getUserId() == null ||
                    request.getUserId().trim().isEmpty()) {

                return ResponseEntity
                        .badRequest()
                        .body(errorResponse(
                                "User ID is required."
                        ));
            }

            if (request.getPassword() == null ||
                    request.getPassword().isEmpty()) {

                return ResponseEntity
                        .badRequest()
                        .body(errorResponse(
                                "Password is required."
                        ));
            }


            // ----------------------------------------------------
            // FIND EMPLOYEE
            // ----------------------------------------------------

            RegisterEmployee employee =
                    registerEmployeeService.getEmployeeById(
                            request.getUserId().trim()
                    );

            if (employee == null) {

                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(errorResponse(
                                "Invalid User ID or password."
                        ));
            }


            // ----------------------------------------------------
            // CHECK EMPLOYEE STATUS
            // ----------------------------------------------------

            if (employee.getEmployee() != null &&
                    employee.getEmployee().getEmployeeProfile() != null) {

                String status =
                        employee
                                .getEmployee()
                                .getEmployeeProfile()
                                .getStatus();

                if (status != null &&
                        !"ACTIVE".equalsIgnoreCase(status)) {

                    return ResponseEntity
                            .status(HttpStatus.FORBIDDEN)
                            .body(errorResponse(
                                    "Your account is inactive. Please contact the administrator."
                            ));
                }
            }


            // ----------------------------------------------------
            // CHECK ROLE
            // ----------------------------------------------------

            if (employee.getRole() == null ||
                    employee.getRole().trim().isEmpty()) {

                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(errorResponse(
                                "Employee role is not configured. Please contact administrator."
                        ));
            }


            // ----------------------------------------------------
            // PASSWORD AUTHENTICATION
            // ----------------------------------------------------

            final String token;

            try {

                token =
                        registerEmployeeService.login(request);

            } catch (RuntimeException ex) {

                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(errorResponse(
                                "Invalid User ID or password."
                        ));
            }


            // ----------------------------------------------------
            // ROLE
            // ----------------------------------------------------

            String role =
                    normalizeRole(employee.getRole());


            // ----------------------------------------------------
            // LOGIN-TIME GPS CHECK: work mode is never chosen by the user
            // ----------------------------------------------------
            Double latitude = request.getLatitude();
            Double longitude = request.getLongitude();
            Double accuracy = request.getAccuracy();
            double distance;
            String workMode;

            if (latitude == null || longitude == null ||
                    !Double.isFinite(latitude) || !Double.isFinite(longitude) ||
                    latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                return ResponseEntity.badRequest().body(errorResponse(
                        "Location is required to determine WFO/WFH. Please enable location and try again."));
            }

            if (accuracy == null || !Double.isFinite(accuracy) || accuracy < 0 || accuracy > 150) {
                return ResponseEntity.badRequest().body(errorResponse(
                        "GPS accuracy is insufficient. Please retry after your location becomes more accurate."));
            }

            var office = geofenceService.getOffice();
            if (office == null || office.getLatitude() == null || office.getLongitude() == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse(
                        "Office location is not configured. Please contact HR/Admin."));
            }

            distance = geofenceService.getDistance(latitude, longitude);
            if (!Double.isFinite(distance)) {
                return ResponseEntity.badRequest().body(errorResponse(
                        "Unable to verify your location. Please retry."));
            }
            double allowedRadius = office.getAllowedRadius() == null ? 200.0 : office.getAllowedRadius();
            workMode = distance <= allowedRadius ? "WFO" : "WFH";

            System.out.println("LOGIN LOCATION: user=" + employee.getUserId()
                    + ", distanceMeters=" + Math.round(distance)
                    + ", radiusMeters=" + allowedRadius + ", detectedMode=" + workMode);

            // ====================================================
            // SAVE ATTENDANCE
            // ====================================================

            saveAttendance(
                    employee.getUserId(),
                    workMode
            );


            // ====================================================
            // CREATE USER SESSION
            // ====================================================

            LocalDateTime loginTime =
                    LocalDateTime.now();

            UserSession userSession =
                    new UserSession();

            userSession.setEmployeeId(
                    employee.getUserId()
            );

            userSession.setJwtToken(
                    token
            );

            userSession.setIsActive(
                    true
            );

            userSession.setCreatedAt(
                    loginTime
            );

            userSession.setExpiresAt(
                    loginTime.plusDays(30)
            );

            userSessionRepository.save(
                    userSession
            );


            // ====================================================
            // LOGIN ACTIVITY
            // ====================================================

            logActivity(
                    employee.getUserId(),
                    employee.getName(),
                    role,
                    "AUTH",
                    "LOGIN",
                    "SUCCESS",
                    "Successful "
                            + workMode
                            + " login",
                    null
            );


            // ====================================================
            // SAVE LOGIN LOCATION
            // ====================================================

            List<LoginLocation> previousLocations = loginLocationService.getByEmployeeId(employee.getUserId());
            LoginLocation previousToday = previousLocations.stream()
                    .filter(row -> LocalDate.now().equals(row.getLoginDate()))
                    .findFirst().orElse(null);
            boolean notifyMode = previousToday == null || !workMode.equalsIgnoreCase(previousToday.getWorkMode());

            LoginLocation loginLocation = LoginLocation.builder()
                    .employeeId(employee.getUserId())
                    .employeeName(employee.getName())
                    .latitude(latitude)
                    .longitude(longitude)
                    .accuracyMeters(accuracy)
                    .distanceFromOffice(distance)
                    .workMode(workMode)
                    .loginStatus("SUCCESS")
                    .loginDate(LocalDate.now())
                    .loginTime(loginTime)
                    .build();
            loginLocationService.save(loginLocation);
            if (notifyMode) notifyWorkMode(employee, workMode, loginTime, distance);


            // ====================================================
            // PERMISSIONS
            // ====================================================

            List<String> permissions =
                    getPermissions(role);


            // ====================================================
            // LOGIN RESPONSE
            // ====================================================

            LoginResponse response =
                    new LoginResponse();

            response.setSuccess(true);

            response.setMessage(
                    "Login successful. Work mode detected: " + workMode + "."
            );
            response.setWorkMode(workMode);

            response.setToken(
                    token
            );

            response.setUserId(
                    employee.getUserId()
            );

            response.setRole(
                    role
            );

            response.setFullName(
                    employee.getName()
            );

            response.setDesignation(
                    employee.getDesignation()
            );

            response.setPermissions(
                    permissions
            );


            System.out.println();
            System.out.println("================================================");
            System.out.println("LOGIN SUCCESS");
            System.out.println("================================================");
            System.out.println("User ID  : "
                    + employee.getUserId());
            System.out.println("Role     : "
                    + role);
            System.out.println("WorkMode : "
                    + workMode);
            System.out.println("================================================");


            return ResponseEntity.ok(response);


        } catch (Exception ex) {

            ex.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse(
                            "Unable to process login. Please try again."
                    ));
        }
    }


    // ============================================================
    // WORK SESSION / MULTI SIGN-OFF
    // ============================================================

    /**
     * Standard work target used by the employee portal. Sign Off closes only
     * the current work interval; employees can resume work later and sign off
     * again. The daily total is the sum of all intervals.
     */
    private static final long REQUIRED_WORK_MINUTES = 9L * 60L;

    @GetMapping("/work-session/today")
    public ResponseEntity<?> todayWorkSession(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("Not authenticated."));
        }

        String employeeId = authentication.getName();
        expiredWorkSessionService.closeExpiredForUser(employeeId);
        LocalDate today = LocalDate.now();
        List<AttendanceTracking> intervals = attendanceTrackingRepository
                .findByEmployeeIdAndDateOrderByLoginTimeAsc(employeeId, today);

        return ResponseEntity.ok(dailyWorkSessionMap(employeeId, today, intervals));
    }

    /**
     * Closes the currently active work interval without logging the user out of
     * Bluethink HRMS. This can be used multiple times in the same day.
     */
    @PostMapping("/signoff")
    public ResponseEntity<?> signoff(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("Not authenticated."));
        }

        String employeeId = authentication.getName();
        expiredWorkSessionService.closeExpiredForUser(employeeId);
        LocalDate today = LocalDate.now();

        Optional<AttendanceTracking> activeOptional = attendanceTrackingRepository
                .findTopByEmployeeIdAndDateAndLogoutTimeIsNullOrderByLoginTimeDesc(employeeId, today);

        if (activeOptional.isEmpty()) {
            List<AttendanceTracking> intervals = attendanceTrackingRepository
                    .findByEmployeeIdAndDateOrderByLoginTimeAsc(employeeId, today);
            Map<String, Object> response = new LinkedHashMap<>(
                    dailyWorkSessionMap(employeeId, today, intervals));
            response.put("success", false);
            response.put("message", intervals.isEmpty()
                    ? "No active work session was found. Please login again."
                    : "Your work is already paused. Choose Resume Work when you are ready to continue.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        AttendanceTracking active = activeOptional.get();
        LocalDateTime signoffTime = LocalDateTime.now();
        active.setLogoutTime(signoffTime);

        long intervalMinutes = durationMinutes(active.getLoginTime(), signoffTime);
        active.setWorkingHours(roundHours(intervalMinutes));
        active.setStatus("SIGNED_OFF");
        attendanceTrackingRepository.save(active);

        List<AttendanceTracking> intervals = attendanceTrackingRepository
                .findByEmployeeIdAndDateOrderByLoginTimeAsc(employeeId, today);
        Map<String, Object> response = new LinkedHashMap<>(
                dailyWorkSessionMap(employeeId, today, intervals));
        response.put("success", true);
        response.put("action", "SIGNED_OFF");
        response.put("lastSegmentMinutes", intervalMinutes);
        response.put("message", "Work interval signed off. You remain logged in and can resume work at any time.");
        return ResponseEntity.ok(response);
    }

    /**
     * Starts another work interval for the same day. No password/JWT login is
     * required because this is attendance state, not authentication state.
     */
    @PostMapping("/work-session/resume")
    public ResponseEntity<?> resumeWork(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("Not authenticated."));
        }

        String employeeId = authentication.getName();
        expiredWorkSessionService.closeExpiredForUser(employeeId);
        LocalDate today = LocalDate.now();

        Optional<AttendanceTracking> activeOptional = attendanceTrackingRepository
                .findTopByEmployeeIdAndDateAndLogoutTimeIsNullOrderByLoginTimeDesc(employeeId, today);

        if (activeOptional.isPresent()) {
            List<AttendanceTracking> intervals = attendanceTrackingRepository
                    .findByEmployeeIdAndDateOrderByLoginTimeAsc(employeeId, today);
            Map<String, Object> response = new LinkedHashMap<>(
                    dailyWorkSessionMap(employeeId, today, intervals));
            response.put("success", true);
            response.put("action", "ALREADY_ACTIVE");
            response.put("message", "Your work session is already active.");
            return ResponseEntity.ok(response);
        }

        AttendanceTracking latest = attendanceTrackingRepository
                .findTopByEmployeeIdAndDateOrderByLoginTimeDesc(employeeId, today);

        AttendanceTracking interval = new AttendanceTracking();
        interval.setEmployeeId(employeeId);
        interval.setDate(today);
        interval.setLoginTime(LocalDateTime.now());
        interval.setLogoutTime(null);
        interval.setWorkingHours(null);
        interval.setWorkMode(latest != null && latest.getWorkMode() != null
                ? latest.getWorkMode()
                : "WFO");
        interval.setStatus("PRESENT");

        RegisterEmployee employee = registerEmployeeService.getEmployeeById(employeeId);
        if (employee != null) {
            interval.setEmployeeName(employee.getName());
        }

        attendanceTrackingRepository.save(interval);

        List<AttendanceTracking> intervals = attendanceTrackingRepository
                .findByEmployeeIdAndDateOrderByLoginTimeAsc(employeeId, today);
        Map<String, Object> response = new LinkedHashMap<>(
                dailyWorkSessionMap(employeeId, today, intervals));
        response.put("success", true);
        response.put("action", "RESUMED");
        response.put("message", "Work resumed. Your next Sign Off will add this interval to today's total.");
        return ResponseEntity.ok(response);
    }

    /**
     * Returns one summary row per day even when the employee signed off and
     * resumed multiple times during that day.
     */
    @GetMapping("/work-session/history")
    public ResponseEntity<?> workSessionHistory(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("Not authenticated."));
        }

        String employeeId = authentication.getName();
        expiredWorkSessionService.closeExpiredForUser(employeeId);
        List<AttendanceTracking> all = attendanceTrackingRepository
                .findByEmployeeIdOrderByDateDescLoginTimeAsc(employeeId);

        Map<LocalDate, List<AttendanceTracking>> grouped = new LinkedHashMap<>();
        for (AttendanceTracking row : all) {
            if (row == null || row.getDate() == null) continue;
            grouped.computeIfAbsent(row.getDate(), key -> new ArrayList<>()).add(row);
        }

        List<Map<String, Object>> history = new ArrayList<>();
        for (Map.Entry<LocalDate, List<AttendanceTracking>> entry : grouped.entrySet()) {
            history.add(dailyWorkSessionMap(employeeId, entry.getKey(), entry.getValue()));
        }

        return ResponseEntity.ok(history);
    }

    private Map<String, Object> dailyWorkSessionMap(
            String employeeId,
            LocalDate date,
            List<AttendanceTracking> intervals) {

        Map<String, Object> out = new LinkedHashMap<>();
        List<AttendanceTracking> safe = intervals == null ? new ArrayList<>() : intervals;

        LocalDateTime firstLogin = null;
        LocalDateTime latestSignoff = null;
        AttendanceTracking activeInterval = null;
        AttendanceTracking latestInterval = null;
        long totalMinutes = 0L;
        int completedSegments = 0;
        List<Map<String, Object>> segments = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (AttendanceTracking interval : safe) {
            if (interval == null || interval.getLoginTime() == null) continue;

            LocalDateTime loginTime = interval.getLoginTime();
            LocalDateTime logoutTime = interval.getLogoutTime();

            if (firstLogin == null || loginTime.isBefore(firstLogin)) {
                firstLogin = loginTime;
            }
            if (latestInterval == null || loginTime.isAfter(latestInterval.getLoginTime())) {
                latestInterval = interval;
            }

            boolean active = logoutTime == null;
            LocalDateTime effectiveEnd = logoutTime;
            if (active && date != null && date.equals(LocalDate.now())) {
                effectiveEnd = now;
                if (activeInterval == null || loginTime.isAfter(activeInterval.getLoginTime())) {
                    activeInterval = interval;
                }
            }

            // Auto-closed = missing human sign-off: explicitly ZERO unverified minutes.
            boolean unverified = "AUTO_CLOSED_UNVERIFIED".equals(interval.getStatus());
            long segmentMinutes = WorkSessionRules.creditedMinutes(loginTime, effectiveEnd, interval.getStatus());
            totalMinutes += segmentMinutes;

            if (logoutTime != null && !unverified) {
                completedSegments++;
                if (latestSignoff == null || logoutTime.isAfter(latestSignoff)) {
                    latestSignoff = logoutTime;
                }
            }

            Map<String, Object> segment = new LinkedHashMap<>();
            segment.put("id", interval.getId());
            segment.put("loginTime", loginTime);
            segment.put("signoffTime", logoutTime);
            segment.put("workingMinutes", segmentMinutes);
            segment.put("workingHours", roundHours(segmentMinutes));
            segment.put("workMode", interval.getWorkMode());
            segment.put("active", active && date != null && date.equals(LocalDate.now()));
            segment.put("unverified", unverified);
            segment.put("status", interval.getStatus());
            segments.add(segment);
        }

        boolean hasStarted = firstLogin != null;
        boolean active = activeInterval != null;
        boolean priorUnfinished = !hasStarted && date != null && date.equals(LocalDate.now())
                && attendanceTrackingRepository.findByEmployeeIdAndLogoutTimeIsNull(employeeId).stream()
                    .anyMatch(row -> row.getDate() != null && row.getDate().isBefore(date));
        boolean paused = (hasStarted && !active) || priorUnfinished;
        boolean targetMet = totalMinutes >= REQUIRED_WORK_MINUTES;
        long remainingMinutes = WorkSessionRules.remainingMinutes(totalMinutes);
        int progressPercent = (int) Math.min(100L,
                Math.round((totalMinutes * 100.0) / REQUIRED_WORK_MINUTES));

        String status;
        if (!hasStarted) status = priorUnfinished ? "PAUSED" : "NOT_STARTED";
        else if (active) status = "ACTIVE";
        else if (targetMet) status = "TARGET_MET";
        else status = "PAUSED";

        out.put("employeeId", employeeId);
        out.put("date", date);
        out.put("loginTime", firstLogin);
        out.put("firstLoginTime", firstLogin);
        out.put("signoffTime", latestSignoff);
        out.put("logoutTime", latestSignoff);
        out.put("workingHours", roundHours(totalMinutes));
        out.put("workingMinutes", totalMinutes);
        out.put("requiredMinutes", REQUIRED_WORK_MINUTES);
        out.put("remainingMinutes", remainingMinutes);
        out.put("progressPercent", progressPercent);
        out.put("workMode", latestInterval == null ? null : latestInterval.getWorkMode());
        out.put("status", status);
        out.put("active", active);
        out.put("timerRunning", active && !targetMet);
        out.put("paused", paused);
        // Sign Off is interval-level, not day-level. A signed-off interval
        // freezes, while Resume creates a new interval that can be signed off
        // again. Therefore signedOff simply reflects the current paused state.
        out.put("signedOff", hasStarted && !active);
        out.put("targetMet", targetMet);
        out.put("previousDayUnfinished", priorUnfinished);
        out.put("expiredUnverified", safe.stream().anyMatch(row -> "AUTO_CLOSED_UNVERIFIED".equals(row.getStatus())));
        out.put("sessionCount", safe.size());
        out.put("completedSegments", completedSegments);
        out.put("segments", segments);
        return out;
    }

    private long durationMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) return 0L;
        return Math.max(0L, Duration.between(start, end).toMinutes());
    }

    private double roundHours(long minutes) {
        return Math.round((minutes / 60.0) * 100.0) / 100.0;
    }


    // ============================================================
    // CURRENT USER
    // ============================================================

    @GetMapping("/me")
    public ResponseEntity<?> me(
            Authentication authentication) {

        if (authentication == null ||
                authentication.getName() == null) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse(
                            "Not authenticated."
                    ));
        }

        RegisterEmployee employee =
                registerEmployeeService.getEmployeeById(
                        authentication.getName()
                );

        if (employee == null) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(errorResponse(
                            "Employee not found."
                    ));
        }

        Map<String, Object> safeEmployee = new LinkedHashMap<>();
        safeEmployee.put("userId", employee.getUserId());
        safeEmployee.put("fullName", employee.getName());
        safeEmployee.put("email", employee.getEmail());
        safeEmployee.put("phoneNumber", employee.getPhoneNumber());
        safeEmployee.put("role", normalizeRole(employee.getRole()));
        safeEmployee.put("designation", employee.getDesignation());
        safeEmployee.put("departmentId", employee.getDepartmentId());
        safeEmployee.put("reportingManagerId", employee.getReportingManagerId());
        return ResponseEntity.ok(safeEmployee);
    }


    // ============================================================
    // LOGOUT
    // ============================================================

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization) {

        try {

            String token =
                    bearerToken(authorization);

            if (token == null ||
                    token.isBlank()) {

                return ResponseEntity
                        .badRequest()
                        .body(errorResponse(
                                "Authorization token is required."
                        ));
            }


            Optional<UserSession> optionalSession =
                    userSessionRepository
                            .findByJwtToken(token);


            if (optionalSession.isPresent()) {

                UserSession session =
                        optionalSession.get();

                session.setIsActive(false);

                session.setLogoutAt(
                        LocalDateTime.now()
                );

                userSessionRepository.save(
                        session
                );


                RegisterEmployee employee =
                        registerEmployeeService
                                .getEmployeeById(
                                        session.getEmployeeId()
                                );


                if (employee != null) {

                    logActivity(
                            employee.getUserId(),
                            employee.getName(),
                            normalizeRole(
                                    employee.getRole()
                            ),
                            "AUTH",
                            "LOGOUT",
                            "SUCCESS",
                            "Session signed out",
                            null
                    );
                }
            }


            return ResponseEntity.ok(
                    java.util.Map.of(
                            "success",
                            true,
                            "message",
                            "Logout successful."
                    )
            );


        } catch (Exception ex) {

            ex.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse(
                            "Unable to logout."
                    ));
        }
    }


    // ============================================================
    // SAVE ATTENDANCE
    // ============================================================

    /** Notify HR/Admin and the employee's reporting manager after successful location detection. */
    private void notifyWorkMode(RegisterEmployee loggedIn, String workMode,
                                LocalDateTime loginTime, double distance) {
        try {
            Employee employeeRecord = employeeRepository
                    .findByRegisterEmployee_UserId(loggedIn.getUserId())
                    .orElseGet(() -> employeeRepository.findByEmployeeProfile_UserId(loggedIn.getUserId()).orElse(null));
            if (employeeRecord == null) return;

            String message = loggedIn.getName() + " logged in as " + workMode
                    + " at " + loginTime.toString().replace('T', ' ')
                    + " (" + Math.round(distance) + " m from office).";
            java.util.Set<Long> recipientIds = new java.util.LinkedHashSet<>();
            for (Employee candidate : employeeRepository.findAll()) {
                if (candidate.getRegisterEmployee() == null || candidate.getRegisterEmployee().getRole() == null) continue;
                String recipientRole = normalizeRole(candidate.getRegisterEmployee().getRole());
                if ("HR".equals(recipientRole) || "ADMIN".equals(recipientRole)) recipientIds.add(candidate.getId());
            }
            if (employeeRecord.getReportingManager() != null) recipientIds.add(employeeRecord.getReportingManager().getId());
            recipientIds.remove(employeeRecord.getId());
            for (Long recipientId : recipientIds) notificationService.createNotification(recipientId, message);
        } catch (Exception notificationError) {
            // A notification outage must not prevent successful login or attendance recording.
            System.err.println("Unable to send login work-mode notifications: " + notificationError.getMessage());
        }
    }

    private void saveAttendance(
            String employeeId,
            String workMode) {

        try {
            LocalDate today = LocalDate.now();
            expiredWorkSessionService.closeExpiredForUser(employeeId);

            /*
             * AttendanceTracking now stores work intervals, not one immutable
             * row for the entire day. If an interval is already active we only
             * refresh its work mode. If all earlier intervals are closed, a
             * fresh login starts a new interval automatically.
             */
            Optional<AttendanceTracking> activeOptional = attendanceTrackingRepository
                    .findTopByEmployeeIdAndDateAndLogoutTimeIsNullOrderByLoginTimeDesc(
                            employeeId,
                            today
                    );

            AttendanceTracking attendance;
            if (activeOptional.isPresent()) {
                attendance = activeOptional.get();
            } else {
                attendance = new AttendanceTracking();
                attendance.setEmployeeId(employeeId);
                attendance.setDate(today);
                attendance.setLoginTime(LocalDateTime.now());
                attendance.setLogoutTime(null);
                attendance.setWorkingHours(null);

                RegisterEmployee employee = registerEmployeeService.getEmployeeById(employeeId);
                if (employee != null) {
                    attendance.setEmployeeName(employee.getName());
                }
            }

            attendance.setWorkMode(workMode);
            attendance.setStatus("PRESENT");
            attendanceTrackingRepository.save(attendance);

        } catch (Exception ex) {
            /* Attendance failure should not prevent successful authentication. */
            ex.printStackTrace();
        }
    }


    // ============================================================
    // MY SESSIONS
    // ============================================================

    @GetMapping("/sessions")
    public ResponseEntity<?> sessions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("Not authenticated."));
        }

        String employeeId = authentication.getName();
        String currentToken = bearerToken(authorization);

        List<UserSession> sessions = liveSessions(
                userSessionRepository.findByEmployeeIdAndIsActiveTrueOrderByCreatedAtDesc(employeeId)
        );

        return ResponseEntity.ok(convertSessions(sessions, currentToken));
    }


    // ============================================================
    // REVOKE MY SESSION
    // ============================================================

    @PostMapping("/sessions/{id}/revoke")
    public ResponseEntity<?> revokeSession(
            @PathVariable Long id,
            Authentication authentication) {

        if (authentication == null) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse(
                            "Not authenticated."
                    ));
        }


        Optional<UserSession> optionalSession =
                userSessionRepository.findById(id);


        if (optionalSession.isEmpty()) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(errorResponse(
                            "Session not found."
                    ));
        }


        UserSession session =
                optionalSession.get();


        if (!authentication
                .getName()
                .equals(session.getEmployeeId())) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(errorResponse(
                            "You cannot revoke another user's session."
                    ));
        }


        session.setIsActive(false);

        session.setLogoutAt(
                LocalDateTime.now()
        );

        userSessionRepository.save(
                session
        );


        return ResponseEntity.ok(
                java.util.Map.of(
                        "success",
                        true,
                        "message",
                        "Session revoked."
                )
        );
    }


    // ============================================================
    // REVOKE OTHER SESSIONS
    // ============================================================

    @PostMapping("/sessions/revoke-others")
    public ResponseEntity<?> revokeOthers(
            @RequestHeader(
                    value = "Authorization",
                    required = false
            )
            String authorization,
            Authentication authentication) {

        if (authentication == null) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse(
                            "Not authenticated."
                    ));
        }


        String currentToken =
                bearerToken(authorization);


        if (currentToken == null) {

            return ResponseEntity
                    .badRequest()
                    .body(errorResponse(
                            "Authorization token is required."
                    ));
        }


        String employeeId =
                authentication.getName();


        List<UserSession> sessions =
                userSessionRepository
                        .findByEmployeeIdOrderByCreatedAtDesc(
                                employeeId
                        );


        int count = 0;


        for (UserSession session : sessions) {

            if (session.getJwtToken() != null &&
                    !session.getJwtToken().equals(
                            currentToken
                    ) &&
                    Boolean.TRUE.equals(
                            session.getIsActive()
                    )) {

                session.setIsActive(false);

                session.setLogoutAt(
                        LocalDateTime.now()
                );

                userSessionRepository.save(
                        session
                );

                count++;
            }
        }


        return ResponseEntity.ok(
                java.util.Map.of(
                        "success",
                        true,
                        "revoked",
                        count
                )
        );
    }


    // ============================================================
    // SESSION MONITOR
    // ============================================================

    @GetMapping("/session-monitor")
    public ResponseEntity<?> sessionMonitor(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            Authentication authentication) {

        if (authentication == null) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse(
                            "Not authenticated."
                    ));
        }


        RegisterEmployee currentEmployee =
                registerEmployeeService.getEmployeeById(
                        authentication.getName()
                );


        if (currentEmployee == null) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(errorResponse(
                            "Employee not found."
                    ));
        }


        String role =
                normalizeRole(
                        currentEmployee.getRole()
                );


        List<UserSession> sessions;


        // ========================================================
        // ADMIN
        // ========================================================

        if ("ADMIN".equals(role)) {

            sessions =
                    userSessionRepository
                            .findAllByOrderByCreatedAtDesc();
        }


        // ========================================================
        // HR
        // ========================================================

        else if ("HR".equals(role)) {

            List<String> userIds =
                    new ArrayList<>();

            userIds.add(
                    currentEmployee.getUserId()
            );


            String department = null;


            if (currentEmployee.getEmployee() != null &&
                    currentEmployee
                            .getEmployee()
                            .getDepartment() != null) {

                department =
                        currentEmployee
                                .getEmployee()
                                .getDepartment()
                                .getDepartmentName();
            }


            if (department != null &&
                    !department.isBlank()) {

                List<String> departmentUsers =
                        employeeRepository
                                .findUserIdsByDepartment(
                                        department
                                );

                if (departmentUsers != null) {

                    for (String userId :
                            departmentUsers) {

                        if (userId != null &&
                                !userIds.contains(userId)) {

                            userIds.add(userId);
                        }
                    }
                }
            }


            sessions =
                    userIds.isEmpty()
                            ? new ArrayList<>()
                            : userSessionRepository
                            .findByEmployeeIdInOrderByCreatedAtDesc(
                                    userIds
                            );
        }


        // ========================================================
        // MANAGER
        // ========================================================

        else if ("MANAGER".equals(role)) {

            List<String> userIds =
                    new ArrayList<>();


            userIds.add(
                    currentEmployee.getUserId()
            );


            List<String> teamUsers =
                    employeeRepository
                            .findUserIdsByReportingManager(
                                    currentEmployee.getUserId()
                            );


            if (teamUsers != null) {

                for (String userId : teamUsers) {

                    if (userId != null &&
                            !userIds.contains(userId)) {

                        userIds.add(userId);
                    }
                }
            }


            sessions =
                    userSessionRepository
                            .findByEmployeeIdInOrderByCreatedAtDesc(
                                    userIds
                            );
        }


        // ========================================================
        // EMPLOYEE
        // ========================================================

        else {

            sessions =
                    userSessionRepository
                            .findByEmployeeIdOrderByCreatedAtDesc(
                                    currentEmployee.getUserId()
                            );
        }


        sessions = liveSessions(sessions);
        String currentToken = bearerToken(authorization);

        return ResponseEntity.ok(
                convertSessions(
                        sessions,
                        currentToken
                )
        );
    }


    // ============================================================
    // CHANGE PASSWORD
    // ============================================================

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        if (authentication == null) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse(
                            "Not authenticated."
                    ));
        }


        if (request == null ||
                request.getCurrentPassword() == null ||
                request.getNewPassword() == null ||
                request.getCurrentPassword().isBlank() ||
                request.getNewPassword().isBlank()) {

            return ResponseEntity
                    .badRequest()
                    .body(errorResponse(
                            "Current password and new password are required."
                    ));
        }


        RegisterEmployee employee =
                registerEmployeeService.getEmployeeById(
                        authentication.getName()
                );


        if (employee == null) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(errorResponse(
                            "Employee not found."
                    ));
        }


        String storedPassword =
                employee.getPassword();


        if (storedPassword == null ||
                !passwordEncoder.matches(
                        request.getCurrentPassword(),
                        storedPassword
                )) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse(
                            "Current password is incorrect."
                    ));
        }


        employee.setPassword(
                passwordEncoder.encode(
                        request.getNewPassword()
                )
        );


        registerEmployeeService.save(
                employee
        );

        // Password rotation invalidates every existing JWT session for this employee.
        userSessionRepository.deactivateEmployeeSessions(employee.getUserId());

        return ResponseEntity.ok(
                java.util.Map.of(
                        "success",
                        true,
                        "message",
                        "Password changed successfully."
                )
        );
    }


    private List<UserSession> liveSessions(List<UserSession> sessions) {
        List<UserSession> live = new ArrayList<>();
        if (sessions == null) return live;

        LocalDateTime now = LocalDateTime.now();
        for (UserSession session : sessions) {
            if (session == null || !Boolean.TRUE.equals(session.getIsActive())) continue;
            if (session.getLogoutAt() != null) continue;
            if (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(now)) continue;
            live.add(session);
        }
        return live;
    }


    // ============================================================
    // SESSION CONVERSION
    // ============================================================

    private List<UserSessionDTO> convertSessions(
            List<UserSession> sessions,
            String currentToken) {

        List<UserSessionDTO> response =
                new ArrayList<>();


        if (sessions == null) {
            return response;
        }


        for (UserSession session : sessions) {

            boolean active =
                    Boolean.TRUE.equals(
                            session.getIsActive()
                    );


            boolean current =
                    currentToken != null &&
                            currentToken.equals(
                                    session.getJwtToken()
                            );


            UserSessionDTO dto =
                    new UserSessionDTO(
                            session.getId(),
                            session.getEmployeeId(),
                            session.getCreatedAt(),
                            session.getExpiresAt(),
                            session.getLogoutAt(),
                            current,
                            active
                    );


            response.add(dto);
        }


        return response;
    }


    // ============================================================
    // BEARER TOKEN
    // ============================================================

    private String bearerToken(
            String authorization) {

        if (authorization == null ||
                authorization.isBlank()) {

            return null;
        }


        if (!authorization.regionMatches(
                true,
                0,
                "Bearer ",
                0,
                7
        )) {

            return null;
        }


        String token =
                authorization.substring(7).trim();


        return token.isEmpty()
                ? null
                : token;
    }


    // ============================================================
    // NORMALIZE ROLE
    // ============================================================

    private String normalizeRole(
            String role) {

        if (role == null ||
                role.isBlank()) {

            return "EMPLOYEE";
        }


        String value =
                role.trim().toUpperCase();


        if (value.startsWith("ROLE_")) {

            value =
                    value.substring(5);
        }


        return value;
    }


    // ============================================================
    // NORMALIZE WORK MODE
    // ============================================================

    private String normalizeWorkMode(
            String workMode) {

        if (workMode == null ||
                workMode.isBlank()) {

            return "WFO";
        }


        String value =
                workMode.trim().toUpperCase();


        switch (value) {

            case "OFFICE":
            case "WORK_FROM_OFFICE":
            case "WORK FROM OFFICE":
            case "WFO":

                return "WFO";


            case "HOME":
            case "WORK_FROM_HOME":
            case "WORK FROM HOME":
            case "WFH":

                return "WFH";


            default:

                return "WFO";
        }
    }


    // ============================================================
    // PERMISSIONS
    // ============================================================

    private List<String> getPermissions(
            String role) {

        List<String> permissions =
                new ArrayList<>();


        switch (normalizeRole(role)) {

            // ----------------------------------------------------
            // ADMIN
            // ----------------------------------------------------

            case "ADMIN":

                permissions.add("USER_CREATE");
                permissions.add("USER_VIEW");
                permissions.add("USER_EDIT");
                permissions.add("USER_DELETE");

                permissions.add(
                        "ATTENDANCE_VIEW_ALL"
                );

                permissions.add(
                        "LOCATION_VIEW_ALL"
                );

                permissions.add(
                        "ACTIVITY_VIEW_ALL"
                );

                permissions.add(
                        "SESSION_VIEW_ALL"
                );

                permissions.add("REPORT_VIEW");
                permissions.add("SETTINGS_VIEW");

                break;


            // ----------------------------------------------------
            // HR
            // ----------------------------------------------------

            case "HR":

                permissions.add("USER_CREATE");
                permissions.add("USER_VIEW");
                permissions.add("USER_EDIT");

                permissions.add(
                        "ATTENDANCE_VIEW"
                );

                permissions.add(
                        "LOCATION_VIEW"
                );

                permissions.add(
                        "ACTIVITY_VIEW"
                );

                permissions.add(
                        "SESSION_VIEW"
                );

                permissions.add("REPORT_VIEW");

                break;


            // ----------------------------------------------------
            // MANAGER
            // ----------------------------------------------------

            case "MANAGER":

                permissions.add("USER_VIEW");

                permissions.add(
                        "ATTENDANCE_VIEW"
                );

                permissions.add(
                        "LOCATION_VIEW"
                );

                permissions.add(
                        "ACTIVITY_VIEW"
                );

                permissions.add(
                        "SESSION_VIEW"
                );

                permissions.add("TEAM_VIEW");

                break;


            // ----------------------------------------------------
            // EMPLOYEE
            // ----------------------------------------------------

            case "EMPLOYEE":

            default:

                permissions.add(
                        "PROFILE_VIEW"
                );

                permissions.add(
                        "ATTENDANCE_VIEW_SELF"
                );

                permissions.add(
                        "LOCATION_VIEW_SELF"
                );

                permissions.add(
                        "ACTIVITY_VIEW_SELF"
                );

                permissions.add(
                        "SESSION_VIEW_SELF"
                );

                break;
        }


        return permissions;
    }


    // ============================================================
    // ERROR RESPONSE
    // ============================================================

    private LoginResponse errorResponse(
            String message) {

        LoginResponse response =
                new LoginResponse();

        response.setSuccess(false);

        response.setMessage(
                message
        );

        return response;
    }


    // ============================================================
    // ACTIVITY LOG
    // ============================================================

    private void logActivity(
            String userId,
            String employeeName,
            String role,
            String module,
            String action,
            String status,
            String details,
            String ipAddress) {

        try {

            Activity activity =
                    new Activity();

            activity.setUserId(
                    userId
            );

            activity.setEmployeeName(
                    employeeName
            );

            activity.setRole(
                    role
            );

            activity.setModule(
                    module
            );

            activity.setAction(
                    action
            );

            activity.setStatus(
                    status
            );

            activity.setDetails(
                    details
            );

            activity.setIpAddress(
                    ipAddress
            );

            activity.setActivity(
                    action
            );

            activity.setCreatedAt(
                    LocalDateTime.now()
            );

            activityRepository.save(
                    activity
            );

        } catch (Exception ex) {

            /*
             * Activity logging must never break
             * authentication or logout.
             */
            ex.printStackTrace();
        }
    }
}