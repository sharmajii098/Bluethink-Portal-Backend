package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.service.AiActionService;
import EmployeeManagementSystem.service.AiActionTokenStore;
import EmployeeManagementSystem.service.AiRequestDiagnostics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.util.function.Supplier;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Actions are never triggered from /api/ai/chat or automatically by the LLM. */
@RestController
@RequestMapping("/api/ai/actions")
public class AiActionController {
    private final AiActionService actions;
    private final AiRequestDiagnostics diagnostics;
    @Autowired
    public AiActionController(AiActionService actions, AiRequestDiagnostics diagnostics) {
        this.actions = actions; this.diagnostics = diagnostics;
    }
    // Preserve source compatibility with direct controller unit tests.
    public AiActionController(AiActionService actions) { this(actions, new AiRequestDiagnostics()); }

    private <T> T timed(AiRequestDiagnostics.Operation operation, Supplier<T> work) {
        long started = AiRequestDiagnostics.start();
        AiRequestDiagnostics.Outcome outcome = AiRequestDiagnostics.Outcome.SUCCESS;
        try {
            return work.get();
        } catch (AccessDeniedException ex) {
            outcome = AiRequestDiagnostics.Outcome.DENIED;
            throw ex;
        } catch (IllegalArgumentException ex) {
            outcome = AiRequestDiagnostics.Outcome.INVALID;
            throw ex;
        } catch (ResponseStatusException ex) {
            int status = ex.getStatusCode().value();
            outcome = status == 401 || status == 403 ? AiRequestDiagnostics.Outcome.DENIED
                    : ex.getStatusCode().is4xxClientError() ? AiRequestDiagnostics.Outcome.INVALID
                    : AiRequestDiagnostics.Outcome.ERROR;
            throw ex;
        } catch (RuntimeException ex) {
            outcome = AiRequestDiagnostics.Outcome.ERROR;
            throw ex;
        } finally {
            diagnostics.record(operation, outcome, started);
        }
    }

    /** Process-local timings only. This endpoint performs live HRMS admin role verification. */
    @GetMapping("/diagnostics")
    public ResponseEntity<AiRequestDiagnostics.Snapshot> diagnostics() {
        actions.requireAdminForDiagnostics();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(diagnostics.snapshot());
    }
    @PostMapping("/status")
    public ResponseEntity<AiActionService.Status> status(@RequestBody AiActionService.Cancellation request) {
        return timed(AiRequestDiagnostics.Operation.STATUS, () -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(actions.status(request == null ? null : request.token())));
    }
    /** Account-bound and bounded to the previous 24 hours. No confirmation secrets or request details. */
    @GetMapping("/recent")
    public ResponseEntity<java.util.List<AiActionTokenStore.RecentAction>> recent() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(actions.recentActions());
    }

    @GetMapping("/monitor")
    public ResponseEntity<AiActionService.Monitor> monitor(@RequestParam(defaultValue = "24") int hours) {
        return timed(AiRequestDiagnostics.Operation.MONITOR, () -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(actions.monitor(hours)));
    }
    @GetMapping("/review-queue")
    public ResponseEntity<java.util.List<AiActionService.ReviewItem>> reviewQueue() {
        return timed(AiRequestDiagnostics.Operation.REVIEW_QUEUE, () -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(actions.reviewQueue()));
    }
    @PostMapping("/preview")
    public ResponseEntity<AiActionService.Preview> preview(@RequestBody AiActionService.Draft draft) {
        return timed(AiRequestDiagnostics.Operation.PREVIEW, () -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(actions.preview(draft)));
    }
    @PostMapping("/cancel")
    public ResponseEntity<Void> cancel(@RequestBody AiActionService.Cancellation cancellation) {
        return timed(AiRequestDiagnostics.Operation.CANCEL, () -> {
            actions.cancel(cancellation);
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        });
    }
    @PostMapping("/confirm")
    public ResponseEntity<AiActionService.Result> confirm(@RequestBody AiActionService.Confirmation confirmation) {
        return timed(AiRequestDiagnostics.Operation.CONFIRM, () -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(actions.confirm(confirmation)));
    }
}
