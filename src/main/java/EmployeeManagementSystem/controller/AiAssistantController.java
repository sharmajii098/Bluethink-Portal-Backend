package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.service.AiAssistantService;
import EmployeeManagementSystem.service.AiModelUnavailableException;
import EmployeeManagementSystem.service.AiModelBusyException;
import EmployeeManagementSystem.service.AiModelCircuitOpenException;
import EmployeeManagementSystem.service.AiRequestDiagnostics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {
    private static final Logger log = LoggerFactory.getLogger(AiAssistantController.class);
    private final AiAssistantService service;
    private final AiRequestDiagnostics diagnostics;
    private final EmployeeManagementSystem.service.AiAuditService audit;
    private final EmployeeManagementSystem.service.AiHistoryService history;
    @Autowired
    public AiAssistantController(AiAssistantService service, EmployeeManagementSystem.service.AiHistoryService history,
                                 EmployeeManagementSystem.service.AiAuditService audit, AiRequestDiagnostics diagnostics) {
        this.service = service; this.history = history; this.audit = audit; this.diagnostics = diagnostics;
    }
    // Preserve the constructor used in controller unit tests.
    public AiAssistantController(AiAssistantService service, EmployeeManagementSystem.service.AiHistoryService history,
                                 EmployeeManagementSystem.service.AiAuditService audit) {
        this(service, history, audit, new AiRequestDiagnostics());
    }

    public record ChatRequest(String message, String prompt, String contextQuestion) {
        // Maintain compatibility with existing two-argument unit tests and older clients.
        public ChatRequest(String message, String prompt) { this(message, prompt, null); }
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String,String>> chat(@RequestBody ChatRequest request) {
        String message = request == null ? null : (request.message() != null ? request.message() : request.prompt());
        long started = AiRequestDiagnostics.start();
        AiRequestDiagnostics.Outcome outcome = AiRequestDiagnostics.Outcome.SUCCESS;
        try {
            // Reject oversized or empty input before it reaches AI provider or the database.
            String question = EmployeeManagementSystem.service.AiChatDataPolicy.requireQuestion(message);
            String previous = EmployeeManagementSystem.service.AiChatDataPolicy.requireContext(
                    request == null ? null : request.contextQuestion());
            String resolved = EmployeeManagementSystem.service.AiFollowupResolver.resolve(question, previous);
            String answer = service.reply(resolved);
            try {
                // Private uploaded file excerpts are ephemeral: do not persist them in the chat ledger.
                if (!EmployeeManagementSystem.service.AiOwnedDocumentSearch.handles(resolved) && !EmployeeManagementSystem.service.AiKnowledgeService.handles(resolved))
                    history.save(resolved, answer);
            } catch (DataAccessException ex) {
                // A persistence failure must not hide a successfully generated HRMS answer.
                log.error("Could not save AI chat history; returning the generated answer", ex);
            }
            audit.record("CHAT", "SUCCESS");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("answer", answer, "aiVersion", AiAssistantService.AI_VERSION));
        } catch (IllegalArgumentException ex) {
            outcome = AiRequestDiagnostics.Outcome.INVALID;
            audit.record("CHAT", "INVALID_REQUEST");
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                    .body(Map.of("error", ex.getMessage()));
        } catch (AccessDeniedException ex) {
            outcome = AiRequestDiagnostics.Outcome.DENIED;
            audit.record("CHAT", "DENIED");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "Access denied or session permissions could not be verified."));
        } catch (DataAccessException ex) {
            outcome = AiRequestDiagnostics.Outcome.ERROR;
            audit.record("CHAT", "ERROR");
            // A portal database outage is not an AI provider outage. Avoid misleading recovery advice.
            log.error("BlueThink AI portal data unavailable", ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "Portal records are temporarily unavailable. Please try again later or contact your administrator."));
        } catch (AiModelCircuitOpenException ex) {
            outcome = AiRequestDiagnostics.Outcome.ERROR;
            audit.record("CHAT", "MODEL_RECOVERY");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                    .header("Retry-After", Integer.toString(ex.retryAfterSeconds()))
                    .body(Map.of("error", "BlueThink AI model is temporarily recovering. Please retry after the indicated delay."));
        } catch (AiModelBusyException ex) {
            outcome = AiRequestDiagnostics.Outcome.ERROR;
            audit.record("CHAT", "CAPACITY_LIMIT");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                    .header("Retry-After", "5")
                    .body(Map.of("error", "BlueThink AI is busy answering other questions. Please retry shortly."));
        } catch (AiModelUnavailableException ex) {
            outcome = AiRequestDiagnostics.Outcome.ERROR;
            audit.record("CHAT", "ERROR");
            log.warn("BlueThink AI model unavailable", ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "BlueThink AI model is temporarily unavailable. Try again later or contact your administrator."));
        } catch (Exception ex) {
            outcome = AiRequestDiagnostics.Outcome.ERROR;
            audit.record("CHAT", "ERROR");
            log.error("Unexpected BlueThink AI chat failure", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "BlueThink AI could not complete this request. Please try again or contact your administrator."));
        } finally {
            diagnostics.record(AiRequestDiagnostics.Operation.CHAT, outcome, started);
        }
    }
}
