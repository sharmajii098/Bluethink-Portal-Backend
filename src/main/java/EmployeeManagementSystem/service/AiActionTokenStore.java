package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.AiActionPreview;
import EmployeeManagementSystem.repository.AiActionPreviewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Durable, distributed one-time preview reservations. No fallback to in-memory confirmations. */
@Service
public class AiActionTokenStore {
    private final AiActionPreviewRepository previews;
    private final ObjectMapper json;
    public record Pending(String username, String role, String type, Map<String,String> fields,
                          Instant expiresAt, String targetEmployeeId) {}

    public AiActionTokenStore(AiActionPreviewRepository previews, ObjectMapper json) {
        this.previews = previews;
        this.json = json;
    }

    static String digest(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{32,100}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid preview token.");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }

    /** State check reveals no payload, actor ID or target; a foreign token looks like a missing token. */
    public record TokenStatus(String type, String state, Instant expiresAt, String reference) {}
    @Transactional(readOnly = true)
    public TokenStatus status(String token, String actor, String role) {
        // Status/recovery never needs to hydrate or deserialize the stored form.
        // Ownership is checked in the JPQL WHERE clause before any fields are read.
        AiActionPreviewRepository.TokenSummaryRow row = previews.findTokenSummaryForAccount(
                digest(token), actor, role).orElse(null);
        if (row == null) return null;
        String state = "PREPARED".equals(row.getState()) && !row.getExpiresAt().isAfter(Instant.now())
                ? "EXPIRED" : row.getState();
        if ("CONSUMED".equals(state) && row.getCommittedAt() != null) state = "COMMITTED";
        return new TokenStatus(row.getActionType(), state, row.getExpiresAt(),
                "COMMITTED".equals(state) ? row.getBusinessReference() : null);
    }

    /** Recovery history is assembled server-side: no token hash, target employee, or action payload is exposed. */
    public record RecentAction(String type, String state, Instant createdAt, String reference) {}

    @Transactional(readOnly = true)
    public java.util.List<RecentAction> recentForAccount(String actor, String role) {
        Instant now = Instant.now();
        return previews.findRecentActionSummaries(
                        actor, role, now.minus(24, ChronoUnit.HOURS), PageRequest.of(0, 12))
                .stream()
                .map(row -> {
                    String state = row.getState();
                    if ("PREPARED".equals(state) && !row.getExpiresAt().isAfter(now)) state = "EXPIRED";
                    if ("CONSUMED".equals(state) && row.getCommittedAt() != null) state = "COMMITTED";
                    return new RecentAction(row.getActionType(), state, row.getCreatedAt(),
                            "COMMITTED".equals(state) ? row.getBusinessReference() : null);
                }).toList();
    }

    public record MonitorCounts(long active, long expired, long consumed, long cancelled, long recordedCommits) {}
    @Transactional(readOnly = true)
    public MonitorCounts monitor(Instant since, Instant now) {
        return new MonitorCounts(
                previews.countByStateAndExpiresAtAfterAndCreatedAtAfter("PREPARED", now, since),
                previews.countByStateAndExpiresAtLessThanEqualAndCreatedAtAfter("PREPARED", now, since),
                previews.countByStateAndCreatedAtAfter("CONSUMED", since),
                previews.countByStateAndCreatedAtAfter("CANCELLED", since),
                previews.countByCommittedAtAfter(since));
    }

    public long activeCount(String actor) {
        return previews.countByActorIdAndStateAndExpiresAtAfter(actor, "PREPARED", Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void issue(String token, String actor, String role, String type, Map<String,String> fields,
                      Instant expiresAt, String targetEmployeeId) {
        try {
            previews.saveAndFlush(new AiActionPreview(digest(token), actor, role, type,
                    json.writeValueAsString(fields), targetEmployeeId, expiresAt));
        } catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to prepare action", ex); }
    }

    @Transactional(readOnly = true)
    public Pending get(String token, String actor, String role) {
        // Never materialize or deserialize another user's action payload, even when the
        // caller knows a syntactically valid token. The repository enforces the scope.
        AiActionPreview row = previews.findByTokenHashAndActorIdAndActorRole(
                digest(token), actor, role).orElse(null);
        if (row == null || !"PREPARED".equals(row.getState()) || !row.getExpiresAt().isAfter(Instant.now()))
            return null;
        try {
            Map<String,String> fields = json.readValue(row.getPayloadJson(), new TypeReference<Map<String,String>>() {});
            return new Pending(row.getActorId(), row.getActorRole(), row.getActionType(),
                    Map.copyOf(fields), row.getExpiresAt(), row.getTargetEmployeeId());
        } catch (JsonProcessingException ex) { throw new IllegalStateException("Stored action draft is unreadable", ex); }
    }

    // Independent transaction intentionally survives a later business error or rollback.
    // A retry after an ambiguous failure MUST NOT trigger the same business action twice.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean consume(String token, String actor, String role) {
        return previews.consumeOnce(digest(token), actor, role, Instant.now()) == 1;
    }

    /** Atomic business completion receipt: must JOIN the caller's business transaction.
     * Business write + receipt commit together; if either fails, both roll back.
     * The earlier one-time token consumption remains committed independently. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCommit(String token, String actor, String role, String businessReference) {
        String reference = businessReference != null && businessReference.length() <= 100 ? businessReference : null;
        if (previews.recordCommit(digest(token), actor, role, reference, Instant.now()) != 1)
            throw new IllegalStateException("Unable to persist a unique AI action commit receipt");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cancel(String token, String actor, String role) {
        return previews.cancelOnce(digest(token), actor, role, Instant.now()) == 1;
    }

    @Scheduled(fixedDelayString = "${bluethink.ai.actions.cleanup-ms:3600000}")
    @Transactional
    public void cleanupExpired() {
        // Keep necessary ledger state but erase descriptions and target IDs after use.
        // Also backfill old releases and redact expired, unsubmitted drafts.
        Instant now = Instant.now();
        previews.redactClosedOrExpired(now);
        // Retain consumed/cancelled metadata until 30 days after expiry for recovery.
        previews.purgeExpired(now.minus(30, ChronoUnit.DAYS));
    }
}
