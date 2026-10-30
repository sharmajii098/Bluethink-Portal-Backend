package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.service.AiActionService;
import EmployeeManagementSystem.service.AiRequestDiagnostics;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiActionControllerDiagnosticsTest {
    @Test void allowsVerifiedAdminOnlyAndDisablesCaching() {
        AiActionService actions = mock(AiActionService.class);
        AiRequestDiagnostics metrics = new AiRequestDiagnostics();
        var controller = new AiActionController(actions, metrics);
        var response = controller.diagnostics();
        verify(actions).requireAdminForDiagnostics();
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        assertEquals("CURRENT_PROCESS_SINCE_START", response.getBody().scope());
    }

    @Test void roleDenialNeverReturnsMetrics() {
        AiActionService actions = mock(AiActionService.class);
        doThrow(new AccessDeniedException("Role changed")).when(actions).requireAdminForDiagnostics();
        var controller = new AiActionController(actions, new AiRequestDiagnostics());
        assertThrows(AccessDeniedException.class, controller::diagnostics);
    }

    @Test void recordsPreviewErrorsWithoutChangingResponseSemantics() {
        AiActionService actions = mock(AiActionService.class);
        AiRequestDiagnostics metrics = new AiRequestDiagnostics();
        when(actions.preview(null)).thenThrow(new IllegalArgumentException("Invalid draft"));
        var controller = new AiActionController(actions, metrics);
        assertThrows(IllegalArgumentException.class, () -> controller.preview(null));
        var preview = metrics.snapshot().operations().stream()
                .filter(stat -> stat.operation() == AiRequestDiagnostics.Operation.PREVIEW)
                .findFirst().orElseThrow();
        assertEquals(1, preview.requests());
        assertEquals(1, preview.invalid());
        assertEquals(0, preview.success());
    }
}
