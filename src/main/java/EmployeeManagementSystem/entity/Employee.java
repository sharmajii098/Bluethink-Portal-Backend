package EmployeeManagementSystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "employees")
@Data
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(value = {
    "profile", "attendanceList", "salaries", "reportingManager", "registerEmployee", "employeeProfile", "image"
}, allowSetters = true)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


//    @OneToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "profile_id")
//    Legacy profile shortcut is not a persisted JPA relationship. The actual
//    mapped relationship is employeeProfile below.
    @Transient
    private EmployeeProfile profile;


    private String fullName;

    private String firstName;

    private String lastName;


    @Column(nullable = false)
    private LocalDate dateOfBirth;


    @Transient
    private long daysLeft;


    @Transient
    private String remainingText;


    @Column(unique = true)
    private String email;


    private String phone;


    private LocalDate joiningDate;


    private String workMode;


    @Lob
    private byte[] image;


    private String imageName;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id")
    private Department department;



    @OneToMany(mappedBy = "employee",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<Attendance> attendanceList = new ArrayList<>();



    // Salary Mapping

    @OneToMany(mappedBy = "employee",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true)
    private List<Salary> salaries = new ArrayList<>();



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporting_manager_id")
    private Employee reportingManager;



    public Employee(){

    }


    public Employee(String firstName,String lastName,String email){
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    @OneToOne
    @JoinColumn(name="register_employee_id", referencedColumnName = "id")
    private RegisterEmployee registerEmployee;

    @OneToOne(mappedBy = "employee",cascade = CascadeType.ALL)
    private EmployeeProfile employeeProfile;

}