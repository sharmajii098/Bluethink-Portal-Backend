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
    private final AttendanceRepository repository;

    @Scheduled(fixedDelay = 60000L)
    @Transactional
    public void expireMissingPunchOuts() {
        for (Attendance attendance : repository.findByCheckInTimeBeforeAndCheckOutTimeIsNull(LocalDateTime.now().minusHours(24))) {
            if (attendance.getCheckInTime() == null || attendance.getCheckOutTime() != null || attendance.getStatus() == AttendanceStatus.UNVERIFIED) continue;
            attendance.setWorkingHours(0.0);
            attendance.setStatus(AttendanceStatus.UNVERIFIED);
            repository.save(attendance);
        }
    }
}
