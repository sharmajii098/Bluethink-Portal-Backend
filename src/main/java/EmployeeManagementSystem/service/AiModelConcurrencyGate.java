package EmployeeManagementSystem.service;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Process-local upper bound on simultaneous LLM calls. Do not hold a slot while
 * querying the HRMS database; verified record answers remain accessible if the
 * optional model is busy. In a multi-replica deployment this limit applies per JVM.
 */
public final class AiModelConcurrencyGate {
    private final Semaphore slots;
    private final int maxConcurrent;

    public record Snapshot(int limit, int active, int available) {}

    public Snapshot snapshot() {
        int available = slots.availablePermits();
        return new Snapshot(maxConcurrent, maxConcurrent - available, available);
    }

    public AiModelConcurrencyGate(int maxConcurrent) {
        if (maxConcurrent < 1 || maxConcurrent > 64)
            throw new IllegalArgumentException("Model concurrency must be between 1 and 64.");
        this.maxConcurrent = maxConcurrent;
        this.slots = new Semaphore(maxConcurrent, true);
    }

    public <T> T withPermit(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        if (!slots.tryAcquire()) throw new AiModelBusyException();
        try {
            return work.get();
        } finally {
            slots.release();
        }
    }
}
