package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.dto.LoginRequest;
import EmployeeManagementSystem.entity.AttendanceTracking;
import EmployeeManagementSystem.entity.UserSession;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.repository.AttendanceTrackingRepository;
import EmployeeManagementSystem.repository.UserSessionRepository;
import EmployeeManagementSystem.service.RegisterEmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin(
        origins = "http://localhost:5173",
        allowCredentials = "true"
)
public class LoginController {

    private final RegisterEmployeeService service;
    private final AttendanceTrackingRepository attendanceTrackingRepository;
    private final JwtUtil jwtUtil;
    private final UserSessionRepository userSessionRepository;


    // ============================================================
    // LOGIN API
    // ============================================================

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {

        try {

            // ----------------------------------------------------
            // Validate request
            // ----------------------------------------------------

            if (request.getUserId() == null ||
                    request.getUserId().trim().isEmpty()) {

                return ResponseEntity
                        .badRequest()
                        .body(errorResponse("User ID is required"));
            }

            if (request.getPassword() == null ||
                    request.getPassword().trim().isEmpty()) {

                return ResponseEntity
                        .badRequest()
                        .body(errorResponse("Password is required"));
            }


            // ----------------------------------------------------
            // Authenticate user
            // ----------------------------------------------------

            String token = service.login(request);


            // ----------------------------------------------------
            // Extract information from JWT
            // ----------------------------------------------------

            String employeeId = request.getUserId();

            String role = jwtUtil.extractRole(token);


            // ----------------------------------------------------
            // Save user session
            // ----------------------------------------------------

            UserSession userSession = new UserSession();

            userSession.setEmployeeId(employeeId);
            userSession.setJwtToken(token);
            userSession.setIsActive(true);
            userSession.setCreatedAt(LocalDateTime.now());
            userSession.setExpiresAt(
                    LocalDateTime.now().plusHours(1)
            );

            userSessionRepository.save(userSession);


            // ----------------------------------------------------
            // Attendance
            // ----------------------------------------------------

            Optional<AttendanceTracking> active =
                    attendanceTrackingRepository
                            .findTopByEmployeeIdAndDateAndLogoutTimeIsNullOrderByLoginTimeDesc(
                                    employeeId,
                                    LocalDate.now()
                            );

            AttendanceTracking attendance;
            if (active.isPresent()) {
                attendance = active.get();
            } else {
                attendance = new AttendanceTracking();
                attendance.setEmployeeId(employeeId);
                attendance.setDate(LocalDate.now());
                attendance.setLoginTime(LocalDateTime.now());
                attendance.setLogoutTime(null);
                attendance.setWorkingHours(null);
            }

            attendance.setWorkMode(request.getWorkMode());
            attendance.setStatus("PRESENT");
            attendanceTrackingRepository.save(attendance);


            // ----------------------------------------------------
            // JSON response for React
            // ----------------------------------------------------

            Map<String, Object> response =
                    new HashMap<>();

            response.put("success", true);
            response.put("message", "Login successful");
            response.put("token", token);
            response.put("userId", employeeId);
            response.put("role", normalizeRole(role));
            response.put("permissions", new String[0]);


            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {

            // Invalid credentials should NOT be HTTP 500
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse(
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : "Invalid credentials"
                    ));

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse(
                            "Internal server error"
                    ));
        }
    }


    // ============================================================
    // LOGOUT
    // ============================================================

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(value = "Authorization",
                    required = false) String authorization
    ) {

        try {

            if (authorization == null ||
                    !authorization.startsWith("Bearer ")) {

                return ResponseEntity.ok(
                        successResponse("Logged out")
                );
            }

            String token =
                    authorization.substring(7);

            Optional<UserSession> session =
                    userSessionRepository
                            .findByJwtTokenAndIsActiveTrue(token);

            if (session.isPresent()) {

                UserSession userSession =
                        session.get();

                userSession.setIsActive(false);

                userSessionRepository.save(userSession);
            }

            return ResponseEntity.ok(
                    successResponse("Logged out successfully")
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse(
                            "Logout failed"
                    ));
        }
    }


    // ============================================================
    // HELPER - NORMALIZE ROLE
    // ============================================================

    private String normalizeRole(String role) {

        if (role == null) {
            return "";
        }

        if (role.startsWith("ROLE_")) {
            return role.substring(5);
        }

        return role;
    }


    // ============================================================
    // ERROR RESPONSE
    // ============================================================

    private Map<String, Object> errorResponse(
            String message
    ) {

        Map<String, Object> response =
                new HashMap<>();

        response.put("success", false);
        response.put("message", message);

        return response;
    }


    // ============================================================
    // SUCCESS RESPONSE
    // ============================================================

    private Map<String, Object> successResponse(
            String message
    ) {

        Map<String, Object> response =
                new HashMap<>();

        response.put("success", true);
        response.put("message", message);

        return response;
    }
}