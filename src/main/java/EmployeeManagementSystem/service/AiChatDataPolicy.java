package EmployeeManagementSystem.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Bounded AI chat payloads and a finite history-retention window. */
public final class AiChatDataPolicy {
    public static final int MAX_QUESTION_CHARS = 2000;
    public static final int MAX_CONTEXT_CHARS = 2000;
    public static final int MAX_SAVED_ANSWER_CHARS = 16000;
    public static final int DEFAULT_RETENTION_DAYS = 90;
    private AiChatDataPolicy() {}

    public static String requireQuestion(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Enter a question for BlueThink AI.");
        String clean = value.trim();
        if (clean.length() > MAX_QUESTION_CHARS)
            throw new IllegalArgumentException("AI questions must be at most 2000 characters.");
        return clean;
    }

    public static String requireContext(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > MAX_CONTEXT_CHARS)
            throw new IllegalArgumentException("Previous question context is too long.");
        return value;
    }

    public static String capAnswer(String value) {
        if (value == null) throw new IllegalArgumentException("Missing assistant response.");
        return value.length() <= MAX_SAVED_ANSWER_CHARS ? value : value.substring(0, MAX_SAVED_ANSWER_CHARS);
    }

    public static Instant cutoff(Instant now, int retentionDays) {
        if (retentionDays < 1 || retentionDays > 365) throw new IllegalArgumentException("Retention days must be between 1 and 365.");
        return now.minus(retentionDays, ChronoUnit.DAYS);
    }

    public static boolean isRetained(Instant createdAt, Instant cutoff) {
        return createdAt != null && !createdAt.isBefore(cutoff);
    }
}
