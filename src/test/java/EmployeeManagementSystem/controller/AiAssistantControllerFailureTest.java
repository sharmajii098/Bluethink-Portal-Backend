package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.service.AiAssistantService;
import EmployeeManagementSystem.service.AiAuditService;
import EmployeeManagementSystem.service.AiHistoryService;
import EmployeeManagementSystem.service.AiModelUnavailableException;
import EmployeeManagementSystem.service.AiModelBusyException;
import EmployeeManagementSystem.service.AiModelCircuitOpenException;
import EmployeeManagementSystem.service.AiRequestDiagnostics;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AiAssistantControllerFailureTest {
    @Test void modelFailureIs502WithoutMisidentifyingPortalDatabase() {
        AiAssistantService assistant = mock(AiAssistantService.class);
        AiHistoryService history = mock(AiHistoryService.class);
        AiAuditService audit = mock(AiAuditService.class);
        AiRequestDiagnostics stats = new AiRequestDiagnostics();
        when(assistant.reply(anyString())).thenThrow(new AiModelUnavailableException("model offline", new RuntimeException("connection refused")));
        var response = new AiAssistantController(assistant, history, audit, stats)
                .chat(new AiAssistantController.ChatRequest("How do I write a timesheet?", null));
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        assertFalse(response.getBody().get("error").contains("connection refused"));
        verify(audit).record("CHAT", "ERROR");
        verifyNoInteractions(history);
        assertEquals(1, stats.snapshot().operations().stream()
                .filter(row -> row.operation() == AiRequestDiagnostics.Operation.CHAT)
                .findFirst().orElseThrow().errors());
    }

    @Test void databaseFailureIs503WithoutBlamingTheModel() {
        AiAssistantService assistant = mock(AiAssistantService.class);
        AiHistoryService history = mock(AiHistoryService.class);
        AiAuditService audit = mock(AiAuditService.class);
        when(assistant.reply(anyString())).thenThrow(new DataAccessResourceFailureException("db credentials private"));
        var response = new AiAssistantController(assistant, history, audit)
                .chat(new AiAssistantController.ChatRequest("Show my attendance", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        assertFalse(response.getBody().get("error").toLowerCase().contains("ollama"));
        assertFalse(response.getBody().get("error").contains("db credentials private"));
        verify(audit).record("CHAT", "ERROR");
        verifyNoInteractions(history);
    }

    @Test void modelCapacityErrorIs503WithRetryAfterAndNoPromptLeak() {
        AiAssistantService assistant = mock(AiAssistantService.class);
        AiHistoryService history = mock(AiHistoryService.class);
        AiAuditService audit = mock(AiAuditService.class);
        AiRequestDiagnostics stats = new AiRequestDiagnostics();
        when(assistant.reply(anyString())).thenThrow(new AiModelBusyException());
        var response = new AiAssistantController(assistant, history, audit, stats)
                .chat(new AiAssistantController.ChatRequest("Explain overtime", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("5", response.getHeaders().getFirst("Retry-After"));
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        assertTrue(response.getBody().get("error").contains("busy"));
        verify(audit).record("CHAT", "CAPACITY_LIMIT");
        verifyNoInteractions(history);
        assertEquals(1, stats.snapshot().operations().stream()
                .filter(row -> row.operation() == AiRequestDiagnostics.Operation.CHAT)
                .findFirst().orElseThrow().errors());
    }

    @Test void repeatedModelFailuresAre503WithCooldownAndNoSensitiveDetails() {
        AiAssistantService assistant = mock(AiAssistantService.class);
        AiHistoryService history = mock(AiHistoryService.class);
        AiAuditService audit = mock(AiAuditService.class);
        when(assistant.reply(anyString())).thenThrow(new AiModelCircuitOpenException(17));
        var response = new AiAssistantController(assistant, history, audit)
                .chat(new AiAssistantController.ChatRequest("Explain my timesheet", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("17", response.getHeaders().getFirst("Retry-After"));
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        assertFalse(response.getBody().get("error").contains("timesheet"));
        verify(audit).record("CHAT", "MODEL_RECOVERY");
        verifyNoInteractions(history);
    }

    @Test void unexpectedFailureIs500WithSafeNeutralMessage() {
        AiAssistantService assistant = mock(AiAssistantService.class);
        AiHistoryService history = mock(AiHistoryService.class);
        AiAuditService audit = mock(AiAuditService.class);
        when(assistant.reply(anyString())).thenThrow(new IllegalStateException("internal secret"));
        var response = new AiAssistantController(assistant, history, audit)
                .chat(new AiAssistantController.ChatRequest("Show my leave", null));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().get("error").contains("internal secret"));
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        verify(audit).record("CHAT", "ERROR");
        verifyNoInteractions(history);
    }
}
