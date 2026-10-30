package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.Attendance;
import EmployeeManagementSystem.enums.AttendanceStatus;
import EmployeeManagementSystem.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/** Expires missing punch-outs without fabricating paid attendance hours. */
@Service
@RequiredArgsConstructor
public class ExpiredAttendancePunchService {
    private final jakarta.persistence.EntityManager entityManager;
    private final AttendanceRepository repository;
    private final EmployeeManagementSystem.repository.EmployeeRepository employees;
    @org.springframework.beans.factory.annotation.Value("${attendance.timezone:Asia/Kolkata}") private String timezone;

    @Scheduled(fixedDelay = 60000L)
    @Transactional
    public void expireMissingPunchOuts() {
        for (Attendance attendance : repository.findByCheckInTimeBeforeAndCheckOutTimeIsNull(LocalDateTime.now(java.time.ZoneId.of(timezone)).minusHours(24))) {
            if(attendance.getEmployee()!=null)employees.lockById(attendance.getEmployee().getId());
            entityManager.refresh(attendance);
            if (attendance.getCheckInTime() == null || attendance.getCheckOutTime() != null || attendance.getStatus() == AttendanceStatus.UNVERIFIED) continue;
            attendance.setWorkingHours(0.0);
            attendance.setStatus(AttendanceStatus.UNVERIFIED);
            repository.save(attendance);
        }
    }
}
