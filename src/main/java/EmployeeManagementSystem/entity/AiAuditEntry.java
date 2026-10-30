package EmployeeManagementSystem.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="ai_audit_events", indexes={@Index(name="idx_ai_audit_actor_time",columnList="actor_id,occurred_at")})
public class AiAuditEntry {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="actor_id",nullable=false,length=100) private String actorId;
 @Column(name="event_type",nullable=false,length=35) private String eventType;
 @Column(name="result",nullable=false,length=25) private String result;
 @Column(name="occurred_at",nullable=false) private Instant occurredAt;
 protected AiAuditEntry(){}
 public AiAuditEntry(String actor,String type,String result){this.actorId=actor;this.eventType=type;this.result=result;this.occurredAt=Instant.now();}
}
