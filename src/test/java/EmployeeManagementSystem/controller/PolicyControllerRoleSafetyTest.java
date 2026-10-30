package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.entity.RegisterEmployee;
import EmployeeManagementSystem.entity.Policy;
import EmployeeManagementSystem.repository.RegisterEmployeeRepository;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.service.PolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runtime coverage on a dependency-enabled Maven test runner. */
class PolicyControllerRoleSafetyTest {
    private final PolicyService service = mock(PolicyService.class);
    private final JwtUtil jwt = mock(JwtUtil.class);
    private final RegisterEmployeeRepository accounts = mock(RegisterEmployeeRepository.class);
    private final PolicyController controller = new PolicyController(service, jwt, accounts);
    private final String bearer = "Bearer synthetic-test-token";

    @Test void staleHrTokenCannotAccessUnpublishedPolicyAdministration() {
        when(jwt.extractUsername("synthetic-test-token")).thenReturn("EMP123");
        when(jwt.extractRole("synthetic-test-token")).thenReturn("HR");
        RegisterEmployee live = new RegisterEmployee();live.setRole("EMPLOYEE");
        when(accounts.findByUserId("EMP123")).thenReturn(live);
        assertThrows(ResponseStatusException.class, () -> controller.all(bearer));
        assertThrows(ResponseStatusException.class, () -> controller.create(bearer, new Policy()));
        verifyNoInteractions(service);
    }

    @Test void currentHrCanListManagementPolicies() {
        when(jwt.extractUsername("synthetic-test-token")).thenReturn("HR001");
        when(jwt.extractRole("synthetic-test-token")).thenReturn("ROLE_HR");
        RegisterEmployee live = new RegisterEmployee();live.setRole("HR");
        when(accounts.findByUserId("HR001")).thenReturn(live);
        when(service.listAll()).thenReturn(java.util.List.of());
        assertEquals(java.util.List.of(), controller.all(bearer));
        verify(service).listAll();
    }
}
