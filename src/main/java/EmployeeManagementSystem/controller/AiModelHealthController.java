package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.service.AiActionService;
import EmployeeManagementSystem.service.AiModelCircuitBreaker;
import EmployeeManagementSystem.service.AiModelConcurrencyGate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only, no-probe status. Never calls AI provider or reads chat/history/employee data. */
@RestController
@RequestMapping("/api/ai")
public class AiModelHealthController {
    private final AiActionService actions;
    private final AiModelCircuitBreaker breaker;
    private final AiModelConcurrencyGate capacity;

    public AiModelHealthController(AiActionService actions, AiModelCircuitBreaker breaker,
                                   AiModelConcurrencyGate capacity) {
        this.actions = actions;
        this.breaker = breaker;
        this.capacity = capacity;
    }

    public record ModelHealth(String scope, AiModelCircuitBreaker.Snapshot circuit,
                              AiModelConcurrencyGate.Snapshot capacity) {}

    @GetMapping("/model-health")
    public ResponseEntity<ModelHealth> modelHealth() {
        // Second authorization check verifies the account's live HRMS role.
        actions.requireAdminForDiagnostics();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ModelHealth("CURRENT_BACKEND_PROCESS", breaker.snapshot(), capacity.snapshot()));
    }
}
