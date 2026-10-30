package EmployeeManagementSystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "login_location", indexes = {
    @Index(name = "idx_login_location_day_employee", columnList = "login_date,employee_id,login_time")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoginLocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "employee_id", nullable = false) private String employeeId;
    @Column(name = "employee_name") private String employeeName;
    @Column(nullable = false) private Double latitude;
    @Column(nullable = false) private Double longitude;
    @Column(name = "accuracy_meters") private Double accuracyMeters;
    @Column(nullable = false) private Double distanceFromOffice;
    @Column(nullable = false) private String workMode;
    @Column(nullable = false) private String loginStatus;
    @Column(name = "login_date", nullable = false) private LocalDate loginDate;
    @Column(name = "login_time", nullable = false) private LocalDateTime loginTime;
}
