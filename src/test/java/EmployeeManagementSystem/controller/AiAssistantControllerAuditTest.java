package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.service.AiAssistantService;
import EmployeeManagementSystem.service.AiAuditService;
import EmployeeManagementSystem.service.AiHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AiAssistantControllerAuditTest {
    @Test
    void deniedChatReturnsForbiddenAndRecordsDeniedWithoutSavingHistory() {
        AiAssistantService assistant = mock(AiAssistantService.class);
        AiHistoryService history = mock(AiHistoryService.class);
        AiAuditService audit = mock(AiAuditService.class);
        when(assistant.reply(anyString())).thenThrow(new AccessDeniedException("Not authorized"));
        var controller = new AiAssistantController(assistant, history, audit);

        var response = controller.chat(new AiAssistantController.ChatRequest(
                "Show employee EMP0003 attendance", null));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        verify(audit).record("CHAT", "DENIED");
        verify(audit, never()).record("CHAT", "SUCCESS");
        verifyNoInteractions(history);
    }

    @Test
    void successfulChatRecordsSuccess() {
        AiAssistantService assistant = mock(AiAssistantService.class);
        AiHistoryService history = mock(AiHistoryService.class);
        AiAuditService audit = mock(AiAuditService.class);
        when(assistant.reply(anyString())).thenReturn("Authorized report");
        var controller = new AiAssistantController(assistant, history, audit);

        var response = controller.chat(new AiAssistantController.ChatRequest("Show my attendance", null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        verify(audit).record("CHAT", "SUCCESS");
        verify(history).save(anyString(), anyString());
    }

    @Test
    void oversizedQuestionsAreRejectedBeforeModelAndHistory() {
        AiAssistantService assistant = mock(AiAssistantService.class);
        AiHistoryService history = mock(AiHistoryService.class);
        AiAuditService audit = mock(AiAuditService.class);
        var controller = new AiAssistantController(assistant, history, audit);
        var result=controller.chat(new AiAssistantController.ChatRequest("x".repeat(2001),null));
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        verifyNoInteractions(assistant,history);
    }

}
