//package EmployeeManagementSystem.entity;
//
//import jakarta.persistence.Entity;
//import jakarta.persistence.GeneratedValue;
//import jakarta.persistence.GenerationType;
//import jakarta.persistence.Id;
//import lombok.Data;
//
//import java.time.LocalDate;
//
//@Entity
////public class Timesheet {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//    private String employeeId;
//    private String employeeName;
//    private LocalDate date;
//    private String hoursWorked;
//    private String taskDescription;
//    private String workMode;
//    private String status = "PENDING";

//}


package EmployeeManagementSystem.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "timesheet")
public class Timesheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String employeeId;

    private String employeeName;

    @Column(name = "date")
    private LocalDate date;

    private Double hoursWorked;

    @Column(length = 1000)
    private String taskDescription;

    private String workMode;

    private String status = "PENDING";

    @Column(name = "approval_comment", length = 1000)
    private String approvalComment;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "project_name")
    private String projectName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_profile_id")
    private EmployeeProfile employee;

    /** Optional learning/shadow assignment used when a junior/trainee logs time under a senior mentor. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentorship_assignment_id")
    private MentorshipAssignment mentorshipAssignment;

    @Column(name = "mentor_review_status", length = 30)
    private String mentorReviewStatus = "NOT_REQUIRED";

    @Column(name = "mentor_review_comment", length = 1000)
    private String mentorReviewComment;

    @Column(name = "mentor_reviewed_by")
    private String mentorReviewedBy;

    @Column(name = "mentor_reviewed_at")
    private java.time.LocalDateTime mentorReviewedAt;

    @Column(name = "manager_reviewed_by")
    private String managerReviewedBy;

    @Column(name = "manager_reviewed_at")
    private java.time.LocalDateTime managerReviewedAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Double getHoursWorked() { return hoursWorked; }
    public void setHoursWorked(Double hoursWorked) { this.hoursWorked = hoursWorked; }
    public String getTaskDescription() { return taskDescription; }
    public void setTaskDescription(String taskDescription) { this.taskDescription = taskDescription; }
    public String getWorkMode() { return workMode; }
    public void setWorkMode(String workMode) { this.workMode = workMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovalComment() { return approvalComment; }
    public void setApprovalComment(String approvalComment) { this.approvalComment = approvalComment; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public EmployeeProfile getEmployee() { return employee; }
    public void setEmployee(EmployeeProfile employee) { this.employee = employee; }
    public MentorshipAssignment getMentorshipAssignment() { return mentorshipAssignment; }
    public void setMentorshipAssignment(MentorshipAssignment mentorshipAssignment) { this.mentorshipAssignment = mentorshipAssignment; }
    public String getMentorReviewStatus() { return mentorReviewStatus; }
    public void setMentorReviewStatus(String mentorReviewStatus) { this.mentorReviewStatus = mentorReviewStatus; }
    public String getMentorReviewComment() { return mentorReviewComment; }
    public void setMentorReviewComment(String mentorReviewComment) { this.mentorReviewComment = mentorReviewComment; }
    public String getMentorReviewedBy() { return mentorReviewedBy; }
    public void setMentorReviewedBy(String mentorReviewedBy) { this.mentorReviewedBy = mentorReviewedBy; }
    public java.time.LocalDateTime getMentorReviewedAt() { return mentorReviewedAt; }
    public void setMentorReviewedAt(java.time.LocalDateTime mentorReviewedAt) { this.mentorReviewedAt = mentorReviewedAt; }
    public String getManagerReviewedBy() { return managerReviewedBy; }
    public void setManagerReviewedBy(String managerReviewedBy) { this.managerReviewedBy = managerReviewedBy; }
    public java.time.LocalDateTime getManagerReviewedAt() { return managerReviewedAt; }
    public void setManagerReviewedAt(java.time.LocalDateTime managerReviewedAt) { this.managerReviewedAt = managerReviewedAt; }

}
