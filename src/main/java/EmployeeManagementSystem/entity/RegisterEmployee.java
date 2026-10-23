package EmployeeManagementSystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
public class RegisterEmployee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userId;
    private String name;
    private String email;
    private String phoneNumber;
    private String password;
    private String role;
    private String otp;
    private LocalDateTime otpExpiryTime;
    private String designation;
    private Long departmentId;
    private Long reportingManagerId;

    @OneToOne(mappedBy = "registerEmployee",cascade=CascadeType.ALL,fetch = FetchType.LAZY)
    private Employee employee;

}
