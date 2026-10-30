package EmployeeManagementSystem.service;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Local failure backoff for AI provider requests. After consecutive model exceptions,
 * reject new inference requests for a cooling period, then allow one trial call.
 * Model-capacity denials do NOT count as provider failures. Portal data lookups
 * are outside the breaker. State is per JVM and deliberately contains no PII.
 * This does not interrupt hung calls or provide a network timeout.
 */
public final class AiModelCircuitBreaker {
    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long coolDownNanos;
    private final int coolDownSeconds;
    private final LongSupplier nanoClock;
    private State state = State.CLOSED;
    private long openedAtNanos;
    private long generation;
    private int failures;

    /** Snapshot is intentionally limited to process-level operational state. */
    public record Snapshot(String state, int consecutiveFailures, int failureThreshold,
                           int retryAfterSeconds, boolean probeInProgress) {}

    public synchronized Snapshot snapshot() {
        int remaining = 0;
        if (state == State.OPEN) {
            long elapsed = nanoClock.getAsLong() - openedAtNanos;
            long nanos = elapsed < 0 ? coolDownNanos : Math.max(0, coolDownNanos - elapsed);
            remaining = (int) Math.max(0, (nanos + 999_999_999L) / 1_000_000_000L);
        }
        return new Snapshot(state.name(), failures, failureThreshold, remaining, state == State.HALF_OPEN);
    }

    public AiModelCircuitBreaker(int failureThreshold, int coolDownSeconds) {
        this(failureThreshold, coolDownSeconds, System::nanoTime);
    }

    /** Package-visible for deterministic tests with a monotonic test clock. */
    AiModelCircuitBreaker(int failureThreshold, int coolDownSeconds, LongSupplier nanoClock) {
        if (failureThreshold < 1 || failureThreshold > 10)
            throw new IllegalArgumentException("Model circuit failure threshold must be between 1 and 10.");
        if (coolDownSeconds < 5 || coolDownSeconds > 300)
            throw new IllegalArgumentException("Model circuit cooldown must be between 5 and 300 seconds.");
        this.failureThreshold = failureThreshold;
        this.coolDownSeconds = coolDownSeconds;
        this.coolDownNanos = coolDownSeconds * 1_000_000_000L;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    public <T> T execute(Supplier<T> call) {
        Objects.requireNonNull(call, "call");
        long ticket = enter();
        try {
            T result = call.get();
            succeeded(ticket);
            return result;
        } catch (AiModelBusyException ex) {
            // The model was never contacted when an inference slot was unavailable.
            deniedByCapacity(ticket);
            throw ex;
        } catch (RuntimeException ex) {
            failed(ticket);
            throw ex;
        }
    }

    private synchronized long enter() {
        if (state == State.OPEN) {
            long elapsed = nanoClock.getAsLong() - openedAtNanos;
            if (elapsed >= 0 && elapsed < coolDownNanos) {
                long remaining = coolDownNanos - elapsed;
                int retrySeconds = (int) Math.max(1L, (remaining + 999_999_999L) / 1_000_000_000L);
                throw new AiModelCircuitOpenException(retrySeconds);
            }
            // Exactly one request may probe the model after the cooldown.
            state = State.HALF_OPEN;
            generation++;
        } else if (state == State.HALF_OPEN) {
            throw new AiModelCircuitOpenException(coolDownSeconds);
        }
        return generation;
    }

    private synchronized void succeeded(long ticket) {
        if (ticket != generation) return; // Ignore a stale in-flight result.
        failures = 0;
        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            generation++;
        }
    }

    private synchronized void failed(long ticket) {
        if (ticket != generation || state == State.OPEN) return;
        if (state == State.HALF_OPEN || ++failures >= failureThreshold) {
            state = State.OPEN;
            openedAtNanos = nanoClock.getAsLong();
            failures = 0;
            generation++;
        }
    }

    private synchronized void deniedByCapacity(long ticket) {
        if (ticket == generation && state == State.HALF_OPEN) {
            // A failed half-open attempt due only to local load cannot prove recovery.
            // Retry after cooldown; do not consume the model failure budget.
            state = State.OPEN;
            openedAtNanos = nanoClock.getAsLong();
            generation++;
        }
    }
}
