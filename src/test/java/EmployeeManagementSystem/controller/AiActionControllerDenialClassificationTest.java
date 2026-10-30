package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.service.AiActionService;
import EmployeeManagementSystem.service.AiRequestDiagnostics;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiActionControllerDenialClassificationTest {
    @Test void forbiddenAndUnauthenticatedAreDenialsNotFormErrors() {
        for (HttpStatus status : new HttpStatus[] {HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN}) {
            AiActionService actions = mock(AiActionService.class);
            when(actions.preview(null)).thenThrow(new ResponseStatusException(status, "Denied"));
            var metrics = new AiRequestDiagnostics();
            var controller = new AiActionController(actions, metrics);
            assertThrows(ResponseStatusException.class, () -> controller.preview(null));
            var stat = metrics.snapshot().operations().stream()
                    .filter(it -> it.operation() == AiRequestDiagnostics.Operation.PREVIEW)
                    .findFirst().orElseThrow();
            assertEquals(1, stat.denied());
            assertEquals(0, stat.invalid());
        }
    }
}
