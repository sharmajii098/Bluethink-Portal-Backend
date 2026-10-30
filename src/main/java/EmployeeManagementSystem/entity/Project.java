// Project.java
package EmployeeManagementSystem.entity;

import EmployeeManagementSystem.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@Data
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Foreign key to EmployeeProfile
    @Column(name = "employee_profile_id")
    private Long employeeProfileId;

    // Relationship to EmployeeProfile
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_profile_id", referencedColumnName = "id", insertable = false, updatable = false)
    private EmployeeProfile employeeProfile;

    @Column(name = "project_name", unique = true, nullable = true)
    private String projectName;

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "client_region")
    private String clientRegion;

    @Column(name = "department_id")
    private Long departmentId;

    private String technology;

    @Column(name = "assigned_employee_id")
    private Long assignedEmployeeId;

    @Column(name = "assigned_employee_name")
    private String assignedEmployeeName;

    /** Manager responsible for the project/team assignment. */
    @Column(name = "project_manager_id")
    private Long projectManagerId;

    @Column(name = "project_manager_name")
    private String projectManagerName;

    @Column(name = "project_type")
    private String projectType;

    @Column(name = "total_cost")
    private Double totalCost;

    @Column(name = "onboarding_date")
    private LocalDate onboardingDate;

    private LocalDate endDate;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private ProjectStatus status;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // For many-to-many relationship with employees (for multiple assignments)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "project_employees",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "employee_id")
    )
    private List<Employee> assignedEmployees = new ArrayList<>();

    // Constructors
    public Project() {}

    public Project(String projectName, String clientName, String clientRegion,
                   Long departmentId, Long assignedEmployeeId, Double totalCost) {
        this.projectName = projectName;
        this.clientName = clientName;
        this.clientRegion = clientRegion;
        this.departmentId = departmentId;
        this.assignedEmployeeId = assignedEmployeeId;
        this.totalCost = totalCost;
        this.status = ProjectStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
    }
}