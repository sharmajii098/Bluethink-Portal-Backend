package EmployeeManagementSystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** Server-only preview data. The browser receives a random token, but the DB stores only its SHA-256 digest. */
@Entity
@Table(name = "ai_action_previews", indexes = {
        @Index(name = "idx_ai_preview_actor_state_expiry", columnList = "actor_id,state,expires_at"),
        @Index(name = "idx_ai_preview_expiry", columnList = "expires_at")
})
public class AiActionPreview {
    @Id @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;
    @Column(name = "actor_id", length = 100, nullable = false)
    private String actorId;
    @Column(name = "actor_role", length = 40, nullable = false)
    private String actorRole;
    @Column(name = "action_type", length = 40, nullable = false)
    private String actionType;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;
    @Column(name = "target_employee_id", length = 100)
    private String targetEmployeeId;
    @Column(name = "state", length = 16, nullable = false)
    private String state;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    // Filled only after the business transaction commits; never stores confirmation secrets.
    @Column(name = "committed_at")
    private Instant committedAt;
    @Column(name = "business_reference", length = 100)
    private String businessReference;

    protected AiActionPreview() {}

    public AiActionPreview(String tokenHash, String actorId, String actorRole, String actionType,
                           String payloadJson, String targetEmployeeId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.actionType = actionType;
        this.payloadJson = payloadJson;
        this.targetEmployeeId = targetEmployeeId;
        this.state = "PREPARED";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.expiresAt = expiresAt;
    }
    public String getActorId() { return actorId; }
    public String getActorRole() { return actorRole; }
    public String getActionType() { return actionType; }
    public String getPayloadJson() { return payloadJson; }
    public String getTargetEmployeeId() { return targetEmployeeId; }
    public String getState() { return state; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCommittedAt() { return committedAt; }
    public String getBusinessReference() { return businessReference; }
}
