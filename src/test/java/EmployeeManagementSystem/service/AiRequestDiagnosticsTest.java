package EmployeeManagementSystem.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiRequestDiagnosticsTest {
    @Test void defaultCountersDoNotLeakUserContentAndStartEmpty() {
        AiRequestDiagnostics diagnostics = new AiRequestDiagnostics();
        var snapshot = diagnostics.snapshot();
        assertEquals("CURRENT_PROCESS_SINCE_START", snapshot.scope());
        assertEquals(7, snapshot.operations().size());
        assertEquals(0, snapshot.operations().get(0).requests());
        assertEquals(256, snapshot.maxSamplesPerOperation());
    }

    @Test void countsOutcomesAndKeepsBoundedLatencySample() {
        AiRequestDiagnostics diagnostics = new AiRequestDiagnostics();
        long started = AiRequestDiagnostics.start();
        for (int i = 0; i < 300; i++) {
            diagnostics.record(AiRequestDiagnostics.Operation.CONFIRM,
                    i % 2 == 0 ? AiRequestDiagnostics.Outcome.SUCCESS : AiRequestDiagnostics.Outcome.ERROR, started);
        }
        var result = diagnostics.snapshot().operations().stream()
                .filter(s -> s.operation() == AiRequestDiagnostics.Operation.CONFIRM).findFirst().orElseThrow();
        assertEquals(300, result.requests());
        assertEquals(150, result.success());
        assertEquals(150, result.errors());
        assertEquals(256, result.latencySamples());
        assertTrue(result.p50Ms() <= result.p95Ms());
        assertTrue(result.p95Ms() <= result.maxMs());
    }

    @Test void rejectingUnknownMetricsKeepsCardinalityFixed() {
        AiRequestDiagnostics diagnostics = new AiRequestDiagnostics();
        assertThrows(IllegalArgumentException.class,
                () -> diagnostics.record(null, AiRequestDiagnostics.Outcome.SUCCESS, 0));
        assertThrows(IllegalArgumentException.class,
                () -> diagnostics.record(AiRequestDiagnostics.Operation.CHAT, null, 0));
    }
}
