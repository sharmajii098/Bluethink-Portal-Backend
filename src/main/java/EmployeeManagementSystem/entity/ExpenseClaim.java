package EmployeeManagementSystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Data @Table(name="expense_claims")
public class ExpenseClaim {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private String userId;
 private String employeeName;
 @Column(nullable=false) private String category;
 @Column(nullable=false) private Double amount;
 @Column(nullable=false) private LocalDate expenseDate;
 private String description;
 private String receiptName;
 private String receiptPath;
 private String status;
 private String approverComment;
 private String approvedBy;
 private LocalDateTime submittedAt;
 private LocalDateTime reviewedAt;
 private LocalDateTime createdAt;
 private LocalDateTime updatedAt;
 @PrePersist void pre(){ createdAt=LocalDateTime.now(); updatedAt=createdAt; if(status==null) status="PENDING"; if(submittedAt==null) submittedAt=createdAt; }
 @PreUpdate void upd(){ updatedAt=LocalDateTime.now(); }
}
