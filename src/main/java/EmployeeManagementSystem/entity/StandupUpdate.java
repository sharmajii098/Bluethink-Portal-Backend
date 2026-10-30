package EmployeeManagementSystem.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="standup_updates", uniqueConstraints=@UniqueConstraint(name="uk_standup_user_date", columnNames={"employee_username","work_date"}))
public class StandupUpdate {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="employee_username",nullable=false,length=120) private String employeeUsername;
 @Column(name="employee_name",length=180) private String employeeName;
 @Column(name="work_date",nullable=false) private LocalDate workDate;
 @Column(name="completed_work",length=4000) private String completedWork;
 @Column(name="planned_work",length=4000) private String plannedWork;
 @Column(name="blockers",length=3000) private String blockers;
 @Column(name="discussion_points",length=2000) private String discussionPoints;
 @Column(name="blocker_status",length=30) private String blockerStatus="NONE";
 @Column(name="action_owner",length=120) private String actionOwner;
 @Column(name="action_due_date") private LocalDate actionDueDate;
 @Column(name="action_notes",length=2000) private String actionNotes;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 @Transient private String timesheetSyncStatus;
 public String getTimesheetSyncStatus(){return timesheetSyncStatus;}
 public void setTimesheetSyncStatus(String value){timesheetSyncStatus=value;}
 @PrePersist void create(){LocalDateTime n=LocalDateTime.now();createdAt=n;updatedAt=n;if(blockerStatus==null)blockerStatus="NONE";}
 @PreUpdate void update(){updatedAt=LocalDateTime.now();}
 public Long getId(){return id;} public String getEmployeeUsername(){return employeeUsername;} public void setEmployeeUsername(String v){employeeUsername=v;}
 public String getEmployeeName(){return employeeName;} public void setEmployeeName(String v){employeeName=v;} public LocalDate getWorkDate(){return workDate;} public void setWorkDate(LocalDate v){workDate=v;}
 public String getCompletedWork(){return completedWork;} public void setCompletedWork(String v){completedWork=v;} public String getPlannedWork(){return plannedWork;} public void setPlannedWork(String v){plannedWork=v;}
 public String getBlockers(){return blockers;} public void setBlockers(String v){blockers=v;} public String getDiscussionPoints(){return discussionPoints;} public void setDiscussionPoints(String v){discussionPoints=v;}
 public String getBlockerStatus(){return blockerStatus;} public void setBlockerStatus(String v){blockerStatus=v;} public String getActionOwner(){return actionOwner;} public void setActionOwner(String v){actionOwner=v;}
 public LocalDate getActionDueDate(){return actionDueDate;} public void setActionDueDate(LocalDate v){actionDueDate=v;} public String getActionNotes(){return actionNotes;} public void setActionNotes(String v){actionNotes=v;}
 public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
