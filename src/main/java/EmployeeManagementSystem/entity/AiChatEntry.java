package EmployeeManagementSystem.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="ai_chat_entries", indexes={@Index(name="idx_ai_chat_owner_created", columnList="owner_id,created_at")})
public class AiChatEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="owner_id", nullable=false, length=100) private String ownerId;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(nullable=false, length=2000) private String question;
    @Lob @Column(name="answer", nullable=false, columnDefinition="LONGTEXT") private String answer;
    protected AiChatEntry() {}
    public AiChatEntry(String ownerId, String question, String answer) {
        this.ownerId=ownerId; this.question=question; this.answer=answer; this.createdAt=Instant.now();
    }
    public Long getId(){return id;}
    public String getOwnerId(){return ownerId;}
    public Instant getCreatedAt(){return createdAt;}
    public String getQuestion(){return question;}
    public String getAnswer(){return answer;}
}
