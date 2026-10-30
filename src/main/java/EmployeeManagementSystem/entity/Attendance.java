package EmployeeManagementSystem.entity;

import EmployeeManagementSystem.enums.AttendanceStatus;
import EmployeeManagementSystem.enums.WorkMode;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "attendance")
@Data
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String correctedBy;
    @Column(length=1000) private String correctionReason;
    private LocalDateTime correctedAt;
    private LocalDateTime checkOutDateTime;

    private LocalDate date;
    // Attendance Date
    private LocalDate attendanceDate;

    // Punch In
    private LocalDateTime checkInTime;

    // Punch Out
    private LocalTime checkOutTime;

    // Working Hours
    private Double workingHours;

    /** Audit pointer: approved attendance regularization, not a raw biometric punch. */
    private Long regularizedByRequestId;
    private LocalDateTime regularizedAt;

    // Attendance Status
    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    private WorkMode workMode;

    //private String employeeId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
}