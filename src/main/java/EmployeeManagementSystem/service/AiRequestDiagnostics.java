package EmployeeManagementSystem.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

/**
 * Low-cardinality, process-local AI request health counters.
 * No questions, usernames, tokens, response bodies or record identifiers are stored.
 * Counters reset on restart and are NOT a substitute for centralized monitoring.
 */
@Service
public class AiRequestDiagnostics {
    public enum Operation { CHAT, PREVIEW, CONFIRM, STATUS, CANCEL, REVIEW_QUEUE, MONITOR }
    public enum Outcome { SUCCESS, INVALID, DENIED, ERROR }

    private static final int MAX_SAMPLES = 256;
    private final Instant startedAt = Instant.now();
    private final EnumMap<Operation, Bucket> buckets = new EnumMap<>(Operation.class);

    public AiRequestDiagnostics() {
        for (Operation operation : Operation.values()) buckets.put(operation, new Bucket());
    }

    public static long start() { return System.nanoTime(); }

    /** Measurement starts before the handler and ends even when the handler throws. */
    public void record(Operation operation, Outcome outcome, long startedNanos) {
        if (operation == null || outcome == null) throw new IllegalArgumentException("Fixed diagnostic category required");
        long elapsed = System.nanoTime() - startedNanos;
        // Ignore impossible clock values; bound a very long-running handler to 24 hours.
        long millis = Math.max(0L, Math.min(86_400_000L, elapsed / 1_000_000L));
        buckets.get(operation).add(outcome, millis);
    }

    public record OperationStats(Operation operation, long requests, long success, long invalid,
                                 long denied, long errors, int latencySamples,
                                 long p50Ms, long p95Ms, long maxMs) {}
    public record Snapshot(Instant processStartedAt, String scope, int maxSamplesPerOperation,
                           List<OperationStats> operations) {}

    public Snapshot snapshot() {
        List<OperationStats> values = new ArrayList<>();
        for (Operation operation : Operation.values()) values.add(buckets.get(operation).snapshot(operation));
        return new Snapshot(startedAt, "CURRENT_PROCESS_SINCE_START", MAX_SAMPLES, List.copyOf(values));
    }

    private static final class Bucket {
        private long requests, success, invalid, denied, errors;
        private final long[] samples = new long[MAX_SAMPLES];
        private int next, size;

        synchronized void add(Outcome outcome, long millis) {
            requests++;
            switch (outcome) {
                case SUCCESS -> success++;
                case INVALID -> invalid++;
                case DENIED -> denied++;
                case ERROR -> errors++;
            }
            samples[next] = millis;
            next = (next + 1) % MAX_SAMPLES;
            size = Math.min(MAX_SAMPLES, size + 1);
        }

        synchronized OperationStats snapshot(Operation operation) {
            long[] sorted = Arrays.copyOf(samples, size);
            Arrays.sort(sorted);
            return new OperationStats(operation, requests, success, invalid, denied, errors, size,
                    percentile(sorted, 50), percentile(sorted, 95), size == 0 ? 0 : sorted[size - 1]);
        }

        private static long percentile(long[] sorted, int percentile) {
            if (sorted.length == 0) return 0;
            int index = (int) Math.ceil(percentile * sorted.length / 100.0) - 1;
            return sorted[Math.max(0, index)];
        }
    }
}
