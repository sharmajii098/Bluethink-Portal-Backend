package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.service.AiActionService;
import EmployeeManagementSystem.service.AiModelCircuitBreaker;
import EmployeeManagementSystem.service.AiModelConcurrencyGate;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiModelHealthControllerTest {
    @Test void verifiedAdminCanReadNoStoreProcessSnapshot() {
        AiActionService actions = mock(AiActionService.class);
        var controller = new AiModelHealthController(actions, new AiModelCircuitBreaker(3, 30),
                new AiModelConcurrencyGate(2));
        var response = controller.modelHealth();
        verify(actions).requireAdminForDiagnostics();
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        assertNotNull(response.getBody());
        assertEquals("CURRENT_BACKEND_PROCESS", response.getBody().scope());
        assertEquals("CLOSED", response.getBody().circuit().state());
        assertEquals(2, response.getBody().capacity().available());
    }

    @Test void accountRoleDenialPreventsHealthResponse() {
        AiActionService actions = mock(AiActionService.class);
        doThrow(new AccessDeniedException("Not an active admin"))
                .when(actions).requireAdminForDiagnostics();
        var controller = new AiModelHealthController(actions, new AiModelCircuitBreaker(3, 30),
                new AiModelConcurrencyGate(2));
        assertThrows(AccessDeniedException.class, controller::modelHealth);
        verify(actions).requireAdminForDiagnostics();
    }
}
