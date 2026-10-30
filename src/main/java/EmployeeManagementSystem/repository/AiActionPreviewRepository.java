package EmployeeManagementSystem.repository;

import EmployeeManagementSystem.entity.AiActionPreview;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiActionPreviewRepository extends JpaRepository<AiActionPreview, String> {
    // Apply ownership at the database boundary before accessing persisted draft fields.
    java.util.Optional<AiActionPreview> findByTokenHashAndActorIdAndActorRole(
            String tokenHash, String actorId, String actorRole);

    /** Narrow database projection for status checks and cancellation preflight.
     * Never SELECT payload_json, target_employee_id or token_hash. Owner/role
     * are predicates, not projected personal data. */
    interface TokenSummaryRow {
        String getActionType();
        String getState();
        Instant getExpiresAt();
        Instant getCommittedAt();
        String getBusinessReference();
    }

    @Query("select p.actionType as actionType, p.state as state, " +
           "p.expiresAt as expiresAt, p.committedAt as committedAt, " +
           "p.businessReference as businessReference from AiActionPreview p " +
           "where p.tokenHash = :hash and p.actorId = :actor and p.actorRole = :role")
    java.util.Optional<TokenSummaryRow> findTokenSummaryForAccount(@Param("hash") String tokenHash,
            @Param("actor") String actorId, @Param("role") String actorRole);

    long countByActorIdAndStateAndExpiresAtAfter(String actorId, String state, Instant now);

    /** Database projection for recovery history: never SELECT a preview's token hash,
     * raw draft payload, target employee, or account identifier. */
    interface RecentActionRow {
        String getActionType();
        String getState();
        Instant getCreatedAt();
        Instant getExpiresAt();
        Instant getCommittedAt();
        String getBusinessReference();
    }

    // Ownership and time window are applied in SQL, with a server-controlled 12-row page.
    @Query("select p.actionType as actionType, p.state as state, p.createdAt as createdAt, " +
           "p.expiresAt as expiresAt, p.committedAt as committedAt, " +
           "p.businessReference as businessReference " +
           "from AiActionPreview p where p.actorId = :actor and p.actorRole = :role " +
           "and p.createdAt > :since order by p.createdAt desc")
    List<RecentActionRow> findRecentActionSummaries(@Param("actor") String actorId,
            @Param("role") String actorRole, @Param("since") Instant since, Pageable limit);

    long countByStateAndCreatedAtAfter(String state, Instant since);
    long countByCommittedAtAfter(Instant since);
    long countByStateAndExpiresAtAfterAndCreatedAtAfter(String state, Instant now, Instant since);
    long countByStateAndExpiresAtLessThanEqualAndCreatedAtAfter(String state, Instant now, Instant since);

    // An atomic compare-and-set in the database makes one-time confirmation safe across replicas.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AiActionPreview p set p.state = 'CONSUMED', p.payloadJson = '{}', " +
           "p.targetEmployeeId = null, p.updatedAt = :now " +
           "where p.tokenHash = :hash and p.actorId = :actor and p.actorRole = :role " +
           "and p.state = 'PREPARED' and p.expiresAt > :now")
    int consumeOnce(@Param("hash") String hash, @Param("actor") String actor,
                    @Param("role") String role, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AiActionPreview p set p.state = 'CANCELLED', p.payloadJson = '{}', " +
           "p.targetEmployeeId = null, p.updatedAt = :now " +
           "where p.tokenHash = :hash and p.actorId = :actor and p.actorRole = :role " +
           "and p.state = 'PREPARED' and p.expiresAt > :now")
    int cancelOnce(@Param("hash") String hash, @Param("actor") String actor,
                   @Param("role") String role, @Param("now") Instant now);

    // The receipt is written inside the business transaction; it becomes durable only on commit.
    // The state remains CONSUMED so existing one-time claim/monitor semantics remain intact.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AiActionPreview p set p.committedAt = :now, p.businessReference = :reference, " +
           "p.updatedAt = :now where p.tokenHash = :hash and p.actorId = :actor " +
           "and p.actorRole = :role and p.state = 'CONSUMED' and p.committedAt is null")
    int recordCommit(@Param("hash") String hash, @Param("actor") String actor,
                     @Param("role") String role, @Param("reference") String reference,
                     @Param("now") Instant now);

    /** Scrub historical draft details without deleting ledger states, receipts or audit counts.
     * Keeps still-valid PREPARED drafts usable. Also backfills older releases' rows. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AiActionPreview p set p.payloadJson = '{}', p.targetEmployeeId = null " +
           "where (p.state in ('CONSUMED', 'CANCELLED') or p.expiresAt <= :now) " +
           "and (p.payloadJson <> '{}' or p.targetEmployeeId is not null)")
    int redactClosedOrExpired(@Param("now") Instant now);

    @Modifying
    @Query("delete from AiActionPreview p where p.expiresAt < :cutoff")
    int purgeExpired(@Param("cutoff") Instant cutoff);
}
