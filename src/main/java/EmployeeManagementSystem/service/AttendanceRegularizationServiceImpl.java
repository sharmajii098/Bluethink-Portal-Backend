package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.Attendance;
import EmployeeManagementSystem.entity.AttendanceRegularization;
import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.enums.AttendanceStatus;
import EmployeeManagementSystem.repository.AttendanceRegularizationRepository;
import EmployeeManagementSystem.repository.AttendanceRepository;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.repository.ShiftAssignmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The approved request and the real attendance row are saved in ONE transaction. */
@Service
@Transactional
public class AttendanceRegularizationServiceImpl implements AttendanceRegularizationService {
    private final AttendanceRegularizationRepository requests;
    private final AttendanceRepository attendance;
    private final EmployeeRepository employees;
    private final ShiftAssignmentRepository shifts;

    @Value("${attendance.timezone:Asia/Kolkata}") private String attendanceTimezone = "Asia/Kolkata";
    private LocalDateTime attendanceNow() { return LocalDateTime.now(ZoneId.of(attendanceTimezone)); }

    @Value("${attendance.office-start:10:00}") private String officeStart;
    @Value("${attendance.grace-minutes:15}") private int graceMinutes;

    public AttendanceRegularizationServiceImpl(AttendanceRegularizationRepository requests,
                                                AttendanceRepository attendance,
                                                EmployeeRepository employees,
                                                ShiftAssignmentRepository shifts) {
        this.requests = requests;
        this.attendance = attendance;
        this.employees = employees;
        this.shifts = shifts;
    }

    @Transactional(readOnly = true)
    public List<AttendanceRegularization> all() { return requests.findAllByOrderByCreatedAtDesc(); }

    @Transactional(readOnly = true)
    public List<AttendanceRegularization> my(String userId) {
        return requests.findByEmployeeUserIdOrderByCreatedAtDesc(userId);
    }

    public AttendanceRegularization create(AttendanceRegularization request, String userId, String name) {
        if (request.getReason() == null || request.getReason().isBlank())
            throw new IllegalArgumentException("A reason is required.");
        validatedCorrection(request); // Reject missing/invalid/future times BEFORE saving a request.
        if (requests.existsByEmployeeUserIdAndAttendanceDateAndStatusIn(userId, request.getAttendanceDate(),
                List.of("SUBMITTED", "UNDER_REVIEW")))
            throw new IllegalArgumentException("An active regularization request already exists for this date.");
        request.setId(null);
        request.setRequestNumber("AR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        request.setEmployeeUserId(userId);
        request.setEmployeeName(name);
        request.setStatus("SUBMITTED");
        // Never trust approval/audit fields supplied by a request body.
        request.setReviewerUserId(null);
        request.setReviewerName(null);
        request.setReviewerComment(null);
        request.setReviewedAt(null);
        request.setAppliedAt(null);
        request.setAppliedAttendanceId(null);
        return requests.save(request);
    }

    public AttendanceRegularization review(Long id, Map<String, Object> input,
                                            String reviewerUserId, String reviewerName) {
        // A row lock stops simultaneous approvals from applying a request more than once.
        AttendanceRegularization request = requests.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));
        if ("APPROVED".equals(request.getStatus()) || "REJECTED".equals(request.getStatus()))
            throw new IllegalArgumentException("This request is already finalized. Use the legacy correction action only for older approved requests.");
        String status = String.valueOf(input.getOrDefault("status", "UNDER_REVIEW")).trim().toUpperCase();
        if (!Set.of("UNDER_REVIEW", "APPROVED", "REJECTED").contains(status))
            throw new IllegalArgumentException("Invalid review status.");
        String comment = input.get("comment") == null ? "" : String.valueOf(input.get("comment")).trim();
        if ("REJECTED".equals(status) && comment.isEmpty())
            throw new IllegalArgumentException("A rejection reason is required.");
        if ("APPROVED".equals(status)) applyCorrection(request);
        request.setStatus(status);
        request.setReviewerUserId(reviewerUserId);
        request.setReviewerName(reviewerName);
        request.setReviewerComment(comment);
        request.setReviewedAt(attendanceNow());
        return requests.saveAndFlush(request);
    }

    /** Explicit, auditable reconciliation for already-APPROVED records from older versions. */
    public AttendanceRegularization applyLegacyApproved(Long id) {
        AttendanceRegularization request = requests.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));
        if (!"APPROVED".equals(request.getStatus()))
            throw new IllegalArgumentException("Only an approved request may be applied.");
        if (request.getAppliedAt() != null)
            throw new IllegalArgumentException("This approval is already applied. No changes were made.");
        applyCorrection(request);
        return requests.saveAndFlush(request);
    }

    private AttendanceRegularizationRules.Correction validatedCorrection(AttendanceRegularization request) {
        try {
            LocalTime scheduledStart = employees.findByRegisterEmployee_UserId(request.getEmployeeUserId())
                    .flatMap(e -> shifts.findByEmployee_IdAndShiftDate(e.getId(), request.getAttendanceDate()))
                    .map(a -> a.getShift().getStartTime())
                    .orElseGet(() -> LocalTime.parse(officeStart));
            return AttendanceRegularizationRules.calculate(request.getAttendanceDate(),
                    request.getRequestedCheckIn(), request.getRequestedCheckOut(), attendanceNow(),
                    scheduledStart, graceMinutes);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid office-start setting; use HH:mm.");
        }
    }

    private void applyCorrection(AttendanceRegularization request) {
        if (request.getAppliedAt() != null)
            throw new IllegalArgumentException("This request has already updated attendance.");
        AttendanceRegularizationRules.Correction correction = validatedCorrection(request);
        Employee employee = employees.findByRegisterEmployee_UserId(request.getEmployeeUserId())
                .orElseThrow(() -> new IllegalArgumentException("This account has no linked employee attendance record. Link the employee profile before approval."));
        employees.lockById(employee.getId());
        Attendance record = attendance.findByEmployeeAndDate(employee, request.getAttendanceDate());
        if (record != null && (record.getStatus() == AttendanceStatus.LEAVE || record.getStatus() == AttendanceStatus.HOLIDAY || record.getStatus() == AttendanceStatus.WEEKEND))
            throw new IllegalArgumentException("Attendance is marked as leave/holiday/weekend; resolve the conflicting status before approving.");
        if (record != null && request.getAttendanceDate().equals(attendanceNow().toLocalDate())
                && record.getCheckInTime() != null && record.getCheckOutTime() == null
                && record.getStatus() != AttendanceStatus.UNVERIFIED)
            throw new IllegalArgumentException("The employee's shift is still open. Punch out first or approve after the shift ends.");
        if (record == null) {
            record = new Attendance();
            record.setEmployee(employee);
            record.setDate(request.getAttendanceDate());
            record.setAttendanceDate(request.getAttendanceDate());
        }
        record.setCheckInTime(correction.checkIn());
        record.setCheckOutTime(correction.checkOut());
        record.setCheckOutDateTime(request.getAttendanceDate().atTime(correction.checkOut()));
        record.setWorkingHours(correction.workingHours());
        record.setStatus(correction.status());
        record.setRegularizedByRequestId(request.getId());
        record.setRegularizedAt(attendanceNow());
        // saveAndFlush makes DB constraint failures abort both the attendance and approval update.
        Attendance saved = attendance.saveAndFlush(record);
        request.setAppliedAttendanceId(saved.getId());
        request.setAppliedAt(attendanceNow());
        // Raw login/signoff tracking remains untouched as an audit log. The employee calendar,
        // attendance reports and HR attendance view read the canonical Attendance table.
    }
}
