package EmployeeManagementSystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {

    private long totalEmployees;
    private long departments;
    private double attendanceRate;
    private double payroll;

    private double totalPayrollCost;
    private double paidSalary;
    private double pendingSalary;

    private long paidEmployees;
    private long pendingEmployees;

    private double averageSalary;
    private double highestSalary;
    private double lowestSalary;

    private List<Integer> joined;
    private List<Integer> left;
    private List<Integer> attendanceTrend;

    private List<String> departmentNames;
    private List<Double> departmentPayroll;

    private List<String> payrollMonths;
    private List<Double> monthlyPayroll;

    private List<String> paymentStatusLabels;
    private List<Long> paymentStatusCounts;
}