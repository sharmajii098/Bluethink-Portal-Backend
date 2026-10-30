package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.Attendance;
import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.entity.EmployeeProfile;
import EmployeeManagementSystem.entity.LeaveRequest;
import EmployeeManagementSystem.entity.Holiday;
import EmployeeManagementSystem.entity.Timesheet;
import EmployeeManagementSystem.entity.Salary;
import EmployeeManagementSystem.entity.SalaryStructure;
import EmployeeManagementSystem.enums.AttendanceStatus;
import EmployeeManagementSystem.enums.LeaveStatus;
import EmployeeManagementSystem.repository.AttendanceRepository;
import EmployeeManagementSystem.repository.EmployeeProfileRepository;
import EmployeeManagementSystem.repository.LeaveRepository;
import EmployeeManagementSystem.repository.HolidayRepository;
import EmployeeManagementSystem.repository.TimesheetRepository;
import EmployeeManagementSystem.repository.AdminSalaryRepo;
import EmployeeManagementSystem.service.MonthlySalaryService;
import EmployeeManagementSystem.service.SalaryStructureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.Month;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
public class MonthlySalaryServiceImpl implements MonthlySalaryService {

    private final AdminSalaryRepo adminSalaryRepo;
    private final SalaryStructureService salaryStructureService;
    private final EmployeeProfileRepository employeeProfileRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRepository leaveRepository;
    private final TimesheetRepository timesheetRepository;
    private final HolidayRepository holidayRepository;
    private final PayrollReconciliationService reconciliation;
    private final PortalActor actor;

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public Salary generateMonthlySalary(Long profileId, String month, Integer year) {
        EmployeeProfile employeeProfile = employeeProfileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Employee Profile not found with id: " + profileId));

        SalaryStructure salaryStructure = salaryStructureService.getSalaryStructureByEmployeeProfileId(profileId);
        String normalizedMonth = normalizeMonth(month);

        Month payrollMonth = Month.valueOf(normalizedMonth.toUpperCase(Locale.ENGLISH));
        YearMonth payrollYearMonth = YearMonth.of(year, payrollMonth);
        if (adminSalaryRepo.existsByEmployeeProfileIdAndMonthAndYear(profileId, normalizedMonth, year)) {
            throw new RuntimeException("Salary already generated for " + normalizedMonth + " " + year);
        }

        PayrollReconciliationService.Approved approved = reconciliation.consume(profileId, payrollYearMonth.toString());
        PayrollReconciliationRules.Totals work = approved.report().totals();
        if (work == null) throw new IllegalStateException("Payroll totals are unavailable");
        double basicSalary = amount(salaryStructure.getBasicSalary());
        double hra = amount(salaryStructure.getHra());
        double conveyance = amount(salaryStructure.getConveyance());
        double medicalAllowance = amount(salaryStructure.getMedicalAllowance());
        double specialAllowance = amount(salaryStructure.getSpecialAllowance());
        double otherAllowance = amount(salaryStructure.getOtherAllowance());
        double allowance = conveyance + medicalAllowance + specialAllowance + otherAllowance;
        double bonus = work.overtimePay().doubleValue();
        double pf = amount(salaryStructure.getPf());
        double esi = amount(salaryStructure.getEsi());
        double professionalTax = amount(salaryStructure.getProfessionalTax());
        double tds = amount(salaryStructure.getTds());
        double loanDeduction = amount(salaryStructure.getLoanDeduction());
        double grossSalary = work.monthlyGross().add(work.overtimePay()).doubleValue();
        double attendanceDeduction = work.lossOfPay().doubleValue();
        double totalDeductions = work.totalDeductions().doubleValue();
        double netSalary = work.netPay().doubleValue();
        Employee employee = employeeProfile.getEmployee();

        Salary salary = new Salary();
        if (employee != null) salary.setEmployee(employee);

        salary.setEmployeeProfileId(profileId);
        salary.setEmployeeProfile(employeeProfile);
        salary.setEmployeeCode(employeeProfile.getUserId());
        salary.setEmployeeName(employeeProfile.getFullName());
        salary.setDepartment(employeeProfile.getDepartment());
        salary.setDesignation(employeeProfile.getDesignation());
        salary.setEmployeeBand(employeeProfile.getEmployeeBand());
        salary.setPanNumber(employeeProfile.getPanNumber());
        salary.setBankName(employeeProfile.getBankName());
        salary.setBankAccountNumber(employeeProfile.getAccountNumber());
        salary.setJoiningDate(employee == null ? null : employee.getJoiningDate());

        salary.setBasicSalary(basicSalary);
        salary.setHra(hra);
        salary.setConveyance(conveyance);
        salary.setMedicalAllowance(medicalAllowance);
        salary.setSpecialAllowance(specialAllowance);
        salary.setOtherAllowance(otherAllowance);
        salary.setAllowance(allowance);
        salary.setBonus(bonus);

        salary.setPf(pf);
        salary.setEsi(esi);
        salary.setProfessionalTax(professionalTax);
        salary.setTds(tds);
        salary.setLoanDeduction(loanDeduction);
        salary.setDeductions(totalDeductions);

        salary.setDaysWorked(work.present());
        salary.setLwpDays(work.unpaidLeave());
        salary.setScheduledWorkingDays(work.scheduled());
        salary.setTimesheetPresentDays(work.present());
        salary.setPaidLeaveDays(work.paidLeave());
        salary.setProjectOffDays(work.projectOff());
        salary.setAbsentDays(work.absent());
        salary.setPayableDays(work.payable());
        salary.setAttendanceDeduction(attendanceDeduction);
        salary.setTimesheetCutoffDate(payrollYearMonth.plusMonths(1).atDay(approved.cutoffDay()));
        salary.setGrossSalary(grossSalary);
        salary.setNetSalary(netSalary);
        salary.setPaymentStatus("PENDING");
        salary.setMonth(normalizedMonth);
        salary.setYear(year);
        salary.setPaymentDate(null);
        salary.setGeneratedAt(LocalDateTime.now());
        salary.setGeneratedBy(actor.id());
        salary.setPayrollReconciliationId(approved.reconciliationId());
        salary.setPayrollSourceFingerprint(approved.report().fingerprint());
        salary.setPayrollApprovedBy(approved.approver());
        salary.setOvertimePay(work.overtimePay().doubleValue());

        return adminSalaryRepo.save(salary);
    }

    @Override
    public List<Salary> generateMonthlySalaryForAllEmployees(String month, Integer year) {
        List<EmployeeProfile> activeProfiles = employeeProfileRepository.findAll().stream()
                .filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus()))
                .collect(Collectors.toList());

        java.util.ArrayList<Salary> generated = new java.util.ArrayList<>();
        for (EmployeeProfile profile : activeProfiles) {
            generated.add(generateMonthlySalary(profile.getId(), month, year));
        }
        return generated;
    }

    @Override
    public Salary getSalaryByEmployeeProfileAndMonth(Long profileId, String month, Integer year) {
        return adminSalaryRepo.findByEmployeeProfileIdAndMonthAndYear(profileId, normalizeMonth(month), year)
                .orElseThrow(() -> new RuntimeException("Salary not found for employee profile " + profileId +
                        " for " + month + " " + year));
    }

    @Override
    public List<Salary> getSalariesByMonth(String month, Integer year) {
        return adminSalaryRepo.findByMonthAndYear(normalizeMonth(month), year);
    }

    @Override
    public List<Salary> getSalariesByEmployeeProfile(Long profileId) {
        return adminSalaryRepo.findByEmployeeProfileIdOrderByYearDescMonthDesc(profileId);
    }

    @Override
    public Salary getSalary(Long id) {
        return adminSalaryRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Salary not found with id: " + id));
    }

    private double amount(java.math.BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private String normalizeMonth(String month) {
        if (month == null || month.isBlank()) return LocalDate.now().getMonth().name().substring(0, 1) + LocalDate.now().getMonth().name().substring(1).toLowerCase(Locale.ENGLISH);
        try {
            Month parsed = Month.valueOf(month.trim().toUpperCase(Locale.ENGLISH));
            String lower = parsed.name().toLowerCase(Locale.ENGLISH);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payroll month: " + month);
        }
    }

}
