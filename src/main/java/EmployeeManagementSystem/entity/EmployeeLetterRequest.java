package EmployeeManagementSystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity @Data @Table(name="employee_letter_requests")
public class EmployeeLetterRequest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, unique=true) private String requestNumber;
 @Column(nullable=false) private String employeeUserId;
 private String employeeName;
 @Column(nullable=false) private String letterType;
 private String purpose;
 @Version private Long version;
 private Long templateId;
 @com.fasterxml.jackson.annotation.JsonIgnore @Column(columnDefinition="LONGTEXT") private String preparationInput;
 @com.fasterxml.jackson.annotation.JsonIgnore @Column(columnDefinition="LONGTEXT") private String preparedSnapshot;
 @com.fasterxml.jackson.annotation.JsonIgnore @Column(length=64) private String approvedSnapshotHash;
 @com.fasterxml.jackson.annotation.JsonIgnore @Column(length=64) private String letterSourceFingerprint;
 private String preparedBy;
 private LocalDateTime preparedAt;
 private String letterApprovedBy;
 @Lob @com.fasterxml.jackson.annotation.JsonIgnore @Column(columnDefinition="LONGBLOB") private byte[] issuedPdf;
 @Column(length=64) private String issuedPdfHash;
 // HR verified offer fields; never derive compensation from untrusted employee requests.
 private String designation;
 private java.time.LocalDate joiningDate;
 @Column(precision=14,scale=2) private java.math.BigDecimal annualCtc;
 private Integer probationMonths;
 private Integer noticeDays;
 private java.time.LocalDate offerExpiresOn;
 private String requestedFor;
 @Column(length=2000) private String employeeComment;
 private String status="SUBMITTED";
 private String reviewerUserId;
 private String reviewerName;
 @Column(length=1500) private String hrComment;
 private String documentReference;
 private LocalDateTime reviewedAt;
 private LocalDateTime issuedAt;
 private LocalDateTime createdAt;
 private LocalDateTime updatedAt;
 @PrePersist void pre(){ if(createdAt==null) createdAt=LocalDateTime.now(); updatedAt=createdAt; if(status==null) status="SUBMITTED"; }
 @PreUpdate void upd(){updatedAt=LocalDateTime.now();}
}
