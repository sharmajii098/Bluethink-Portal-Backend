package EmployeeManagementSystem.entity;

import EmployeeManagementSystem.entity.SalaryStructure;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "salary", uniqueConstraints = @UniqueConstraint(name="uq_salary_profile_period", columnNames={"employee_profile_id","month","year"}))
public class Salary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long payrollReconciliationId;
    @Column(length=64) private String payrollSourceFingerprint;
    private String payrollApprovedBy;
    private Double overtimePay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "employee_profile_id")
    private Long employeeProfileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_profile_id", referencedColumnName = "id", insertable = false, updatable = false)
    private EmployeeProfile employeeProfile;

    @Column(name = "employee_code")
    private String employeeCode;

    private String employeeName;
    private String department;
    private String designation;

    private Double basicSalary;
    private Double hra;
    private Double allowance;
    private Double bonus;
    private Double deductions;

    // Salary component snapshot used by the official payslip.  These values are
    // copied at generation time so older payslips do not change when a salary
    // structure is edited later.
    private Double conveyance;
    private Double medicalAllowance;
    private Double specialAllowance;
    private Double otherAllowance;
    private Double pf;
    private Double esi;
    private Double professionalTax;
    private Double tds;
    private Double loanDeduction;

    private Double daysWorked;
    private Double lwpDays;

    // Monthly attendance/timesheet snapshot used for payroll audit.
    // A "present" day is driven by a submitted (non-rejected) timesheet.
    private Double scheduledWorkingDays;
    private Double timesheetPresentDays;
    private Double paidLeaveDays;
    private Double projectOffDays;
    private Double absentDays;
    private Double payableDays;
    private Double attendanceDeduction;
    private LocalDate timesheetCutoffDate;
    private String employeeBand;
    private String panNumber;
    private String bankName;
    private String bankAccountNumber;
    private LocalDate joiningDate;
    private String generatedBy;
    private java.time.LocalDateTime generatedAt;

    private Double grossSalary;
    private Double netSalary;

    private String paymentStatus;

    private String month;
    private Integer year;

    private LocalDate paymentDate;
}