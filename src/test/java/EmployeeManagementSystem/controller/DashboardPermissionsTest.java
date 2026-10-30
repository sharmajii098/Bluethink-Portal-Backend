package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class DashboardPermissionsTest {
    @Mock DashboardService dashboardService;
    @InjectMocks FrontendApiController controller;
    @Test void nonPayrollRolesNeverQueryOrReceiveOrganisationPayroll() {
        for(String role:List.of("EMPLOYEE","MANAGER")) {
            var auth=new UsernamePasswordAuthenticationToken("EMP1",null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
            var data=controller.dashboard(auth);
            assertEquals(Set.of("role"),data.keySet()); assertEquals(role,data.get("role"));
        }
        verifyNoInteractions(dashboardService);
    }
}
