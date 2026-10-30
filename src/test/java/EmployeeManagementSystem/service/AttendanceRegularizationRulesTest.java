package EmployeeManagementSystem.service;

import EmployeeManagementSystem.enums.AttendanceStatus;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;

class AttendanceRegularizationRulesTest {
    private final LocalDate date = LocalDate.of(2026, 9, 21);
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 22, 20, 0);

    @Test void approvalCalculatesHoursAndPresentStatus() {
        var correction = AttendanceRegularizationRules.calculate(date, "2026-09-21T09:30", "18:30", now, LocalTime.of(10, 0), 15);
        assertEquals(9.0, correction.workingHours());
        assertEquals(LocalTime.of(18, 30), correction.checkOut());
        assertEquals(AttendanceStatus.PRESENT, correction.status());
    }

    @Test void lateArrivalUsesConfiguredGracePeriod() {
        var correction = AttendanceRegularizationRules.calculate(date, "2026-09-21T10:16", "19:16", now, LocalTime.of(10, 0), 15);
        assertEquals(AttendanceStatus.LATE, correction.status());
    }

    @Test void rejectsInvalidAndFutureCorrections() {
        assertThrows(IllegalArgumentException.class, () -> AttendanceRegularizationRules.calculate(date, "2026-09-21T11:00", "10:00", now, LocalTime.of(10, 0), 15));
        assertThrows(IllegalArgumentException.class, () -> AttendanceRegularizationRules.calculate(date.plusDays(2), "2026-09-23T09:00", "18:00", now, LocalTime.of(10, 0), 15));
        assertThrows(IllegalArgumentException.class, () -> AttendanceRegularizationRules.calculate(date, "invalid", "18:00", now, LocalTime.of(10, 0), 15));
    }
}
