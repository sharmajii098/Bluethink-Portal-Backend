package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.service.LoginLocationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class LoginLocationVisibilityTest {
    private final LoginLocationService service = mock(LoginLocationService.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final LoginLocationApiController controller = new LoginLocationApiController(service, employees);
    private final LocalDate day = LocalDate.of(2026, 10, 22);
    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken("E01", "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    @Test void unauthenticatedDoesNotReadLocations() {
        assertEquals(401, controller.visible(null, day).getStatusCode().value());
        verifyNoInteractions(service, employees);
    }
    @Test void adminGetsOnlyRequestedDay() {
        when(service.forDay(day)).thenReturn(List.of());
        assertEquals(200, controller.visible(auth("ADMIN"), day).getStatusCode().value());
        verify(service).forDay(day); verify(service, never()).getAllLoginLocations();
    }
    @Test void employeeCannotReadOtherPeople() {
        Employee me = new Employee(); me.setId(1L);
        when(employees.findByRegisterEmployee_UserId("E01")).thenReturn(Optional.of(me));
        when(service.forEmployeesOnDay(Set.of("E01"), day)).thenReturn(List.of());
        controller.visible(auth("EMPLOYEE"), day);
        verify(service).forEmployeesOnDay(Set.of("E01"), day);
        verify(service, never()).forDay(any());
    }
    @Test void missingProfileFailsClosed() {
        when(employees.findByRegisterEmployee_UserId("E01")).thenReturn(Optional.empty());
        assertEquals(404, controller.visible(auth("MANAGER"), day).getStatusCode().value());
        verifyNoInteractions(service);
    }
}
