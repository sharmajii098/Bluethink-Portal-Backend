package EmployeeManagementSystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
@Entity
@Data
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String employeeId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(length = 71)
    private String jwtToken;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private LocalDateTime logoutAt;

}
