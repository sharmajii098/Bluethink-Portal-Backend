package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeMilestoneServiceTest {
    @Mock private EmployeeRepository employeeRepository;
    @InjectMocks private EmployeeMilestoneService service;

    @Test
    void upcomingIncludesBirthdaysAndOnlyCompletedWorkAnniversaries() {
        LocalDate today = LocalDate.now();
        Employee birthday = new Employee();
        birthday.setId(1L);
        birthday.setFullName("Birthday Colleague");
        birthday.setDateOfBirth(today);

        Employee anniversary = new Employee();
        anniversary.setId(2L);
        anniversary.setFullName("Long Service Colleague");
        anniversary.setJoiningDate(today.plusDays(3).minusYears(3));

        Employee futureJoiner = new Employee();
        futureJoiner.setId(3L);
        futureJoiner.setFullName("Future Joiner");
        futureJoiner.setJoiningDate(today.plusDays(1));

        when(employeeRepository.findAllActiveEmployees()).thenReturn(List.of(birthday, anniversary, futureJoiner));

        List<Map<String, Object>> result = service.upcoming(30);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(item -> "BIRTHDAY".equals(item.get("type"))));
        Map<String, Object> workAnniversary = result.stream()
                .filter(item -> "WORK_ANNIVERSARY".equals(item.get("type"))).findFirst().orElseThrow();
        assertEquals(3, workAnniversary.get("yearsCompleted"));
        assertEquals(3L, workAnniversary.get("daysUntil"));
        assertFalse(result.stream().anyMatch(item -> "Future Joiner".equals(item.get("employeeName"))));
    }

    @Test
    void upcomingRejectsUnboundedWindows() {
        assertThrows(IllegalArgumentException.class, () -> service.upcoming(0));
        assertThrows(IllegalArgumentException.class, () -> service.upcoming(367));
    }

    @Test
    void monthRejectsInvalidMonth() {
        assertThrows(IllegalArgumentException.class, () -> service.month(2026, 13));
    }
}
