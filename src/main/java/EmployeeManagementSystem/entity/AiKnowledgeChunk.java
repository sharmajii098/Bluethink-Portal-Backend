package EmployeeManagementSystem.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="ai_knowledge_chunks",indexes={@Index(name="idx_knowledge_source",columnList="source_id"),@Index(name="idx_knowledge_scope",columnList="published,department_id")})
public class AiKnowledgeChunk {
 @Id @Column(length=36) public String id;
 @Column(name="source_id",nullable=false,length=36) public String sourceId;
 @Column(nullable=false,length=160) public String title;
 @Column(nullable=false,length=200) public String filename;
 @Column(nullable=false,length=64) public String checksum;
 @Column(name="allowed_roles",nullable=false,length=64) public String allowedRoles;
 @Column(name="department_id") public Long departmentId;
 @Column(nullable=false) public boolean published;
 @Column(name="created_by",nullable=false,length=100) public String createdBy;
 @Column(name="created_at",nullable=false) public Instant createdAt;
 @Column(name="chunk_index",nullable=false) public int chunkIndex;
 @Column(nullable=false,columnDefinition="TEXT") public String content;
 @Column(nullable=false,columnDefinition="TEXT") public String embedding;
 @Column(name="embedding_model",nullable=false,length=100) public String embeddingModel;
}
