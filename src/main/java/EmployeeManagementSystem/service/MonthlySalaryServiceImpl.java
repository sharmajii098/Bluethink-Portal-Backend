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
@Transactional
public class MonthlySalaryServiceImpl implements MonthlySalaryService {

    private final AdminSalaryRepo adminSalaryRepo;
    private final SalaryStructureService salaryStructureService;
    private final EmployeeProfileRepository employeeProfileRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRepository leaveRepository;
    private final TimesheetRepository timesheetRepository;
    private final HolidayRepository holidayRepository;

    @Override
    public Salary generateMonthlySalary(Long profileId, String month, Integer year) {
        EmployeeProfile employeeProfile = employeeProfileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Employee Profile not found with id: " + profileId));

        SalaryStructure salaryStructure = salaryStructureService.getSalaryStructureByEmployeeProfileId(profileId);
        String normalizedMonth = normalizeMonth(month);

        Month payrollMonth = Month.valueOf(normalizedMonth.toUpperCase(Locale.ENGLISH));
        YearMonth payrollYearMonth = YearMonth.of(year, payrollMonth);
        LocalDate payrollCutoff = payrollYearMonth.plusMonths(1).atDay(2);
        if (LocalDate.now().isBefore(payrollCutoff)) {
            throw new IllegalStateException("Payroll for " + normalizedMonth + " " + year
                    + " can be generated on or after " + payrollCutoff
                    + ". Employees can complete missing timesheets until the 2nd of the next month.");
        }

        if (adminSalaryRepo.existsByEmployeeProfileIdAndMonthAndYear(profileId, normalizedMonth, year)) {
            throw new RuntimeException("Salary already generated for " + normalizedMonth + " " + year);
        }

        double basicSalary = amount(salaryStructure.getBasicSalary());
        double hra = amount(salaryStructure.getHra());
        double conveyance = amount(salaryStructure.getConveyance());
        double medicalAllowance = amount(salaryStructure.getMedicalAllowance());
        double specialAllowance = amount(salaryStructure.getSpecialAllowance());
        double otherAllowance = amount(salaryStructure.getOtherAllowance());
        double allowance = conveyance + medicalAllowance + specialAllowance + otherAllowance;
        double bonus = 0.0;

        double pf = amount(salaryStructure.getPf());
        double esi = amount(salaryStructure.getEsi());
        double professionalTax = amount(salaryStructure.getProfessionalTax());
        double tds = amount(salaryStructure.getTds());
        double loanDeduction = amount(salaryStructure.getLoanDeduction());
        double deductions = pf + esi + professionalTax + tds + loanDeduction;

        double grossSalary = basicSalary + hra + allowance + bonus;

        WorkSummary work = workSummary(employeeProfile, normalizedMonth, year);
        double perWorkingDay = work.scheduledWorkingDays() <= 0 ? 0.0 : grossSalary / work.scheduledWorkingDays();
        double attendanceDeduction = roundMoney(perWorkingDay * (work.absentDays() + work.lwpDays()));
        double totalDeductions = roundMoney(deductions + attendanceDeduction);
        double netSalary = Math.max(0.0, roundMoney(grossSalary - totalDeductions));
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

        salary.setDaysWorked(work.daysWorked());
        salary.setLwpDays(work.lwpDays());
        salary.setScheduledWorkingDays(work.scheduledWorkingDays());
        salary.setTimesheetPresentDays(work.timesheetPresentDays());
        salary.setPaidLeaveDays(work.paidLeaveDays());
        salary.setProjectOffDays(work.projectOffDays());
        salary.setAbsentDays(work.absentDays());
        salary.setPayableDays(work.payableDays());
        salary.setAttendanceDeduction(attendanceDeduction);
        salary.setTimesheetCutoffDate(payrollYearMonth.plusMonths(1).atDay(2));
        salary.setGrossSalary(grossSalary);
        salary.setNetSalary(netSalary);
        salary.setPaymentStatus("PENDING");
        salary.setMonth(normalizedMonth);
        salary.setYear(year);
        salary.setPaymentDate(null);
        salary.setGeneratedAt(LocalDateTime.now());

        return adminSalaryRepo.save(salary);
    }

    @Override
    public List<Salary> generateMonthlySalaryForAllEmployees(String month, Integer year) {
        List<EmployeeProfile> activeProfiles = employeeProfileRepository.findAll().stream()
                .filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus()))
                .collect(Collectors.toList());

        return activeProfiles.stream()
                .map(profile -> {
                    try {
                        return generateMonthlySalary(profile.getId(), month, year);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(salary -> salary != null)
                .collect(Collectors.toList());
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

    private WorkSummary workSummary(EmployeeProfile profile, String month, int year) {
        Month parsed = Month.valueOf(month.toUpperCase(Locale.ENGLISH));
        YearMonth ym = YearMonth.of(year, parsed);
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();

        Employee employee = profile.getEmployee();
        LocalDate employmentStart = employee != null && employee.getJoiningDate() != null && employee.getJoiningDate().isAfter(first)
                ? employee.getJoiningDate() : first;

        Set<LocalDate> importantHolidays = holidayRepository.findByHolidayDateBetween(first, last).stream()
                .filter(this::isPayrollHoliday)
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        Set<LocalDate> timesheetDates = timesheetRepository.findByEmployeeIdAndDateBetween(profile.getUserId(), first, last).stream()
                .filter(t -> !"REJECTED".equalsIgnoreCase(t.getStatus()))
                .map(Timesheet::getDate)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        List<LeaveRequest> approvedLeaves = leaveRepository.findByEmployeeId(profile.getUserId()).stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED && l.getStartDate() != null && l.getEndDate() != null)
                .filter(l -> !l.getStartDate().isAfter(last) && !l.getEndDate().isBefore(first))
                .toList();

        double scheduled = 0.0;
        double present = 0.0;
        double paidLeave = 0.0;
        double projectOff = 0.0;
        double absent = 0.0;
        double lwp = 0.0;

        for (LocalDate d = employmentStart; !d.isAfter(last); d = d.plusDays(1)) {
            final LocalDate workDate = d;
            if (workDate.getDayOfWeek() == DayOfWeek.SATURDAY || workDate.getDayOfWeek() == DayOfWeek.SUNDAY || importantHolidays.contains(workDate)) continue;
            scheduled += 1.0;

            LeaveRequest leave = approvedLeaves.stream()
                    .filter(l -> !workDate.isBefore(l.getStartDate()) && !workDate.isAfter(l.getEndDate()))
                    .findFirst().orElse(null);
            double leavePortion = 0.0;
            String leaveKind = "";
            if (leave != null) {
                String duration = leave.getLeaveDurationType() == null ? "FULL_DAY" : leave.getLeaveDurationType().toUpperCase(Locale.ENGLISH);
                leavePortion = ("FIRST_HALF".equals(duration) || "SECOND_HALF".equals(duration)) ? 0.5 : 1.0;
                leaveKind = leave.getLeaveType() == null ? "" : leave.getLeaveType().toUpperCase(Locale.ENGLISH);
                if (isUnpaid(leaveKind)) lwp += leavePortion;
                else if (leaveKind.contains("PROJECT OFF") || leaveKind.contains("PROJECT_OFF")) projectOff += leavePortion;
                else paidLeave += leavePortion;
            }

            double remaining = Math.max(0.0, 1.0 - leavePortion);
            if (remaining > 0.0) {
                if (timesheetDates.contains(workDate)) present += remaining;
                else absent += remaining;
            }
        }

        double payable = present + paidLeave + projectOff;
        return new WorkSummary(roundDay(scheduled), roundDay(present), roundDay(paidLeave), roundDay(projectOff),
                roundDay(absent), roundDay(lwp), roundDay(payable));
    }

    private boolean isPayrollHoliday(Holiday h) {
        if (h == null || Boolean.FALSE.equals(h.getActive()) || Boolean.TRUE.equals(h.getOptionalHoliday())) return false;
        String type = h.getHolidayType() == null ? "" : h.getHolidayType().toUpperCase(Locale.ENGLISH);
        return type.isBlank() || "PUBLIC".equals(type) || "COMPANY".equals(type);
    }

    private boolean isUnpaid(String type) {
        return type.contains("LEAVE WITHOUT PAY") || type.contains("LWP") || type.contains("UNPAID")
                || type.contains("LOSS OF PAY") || type.contains("LOSS-OF-PAY");
    }

    private double roundMoney(double value) { return Math.round(value * 100.0) / 100.0; }
    private double roundDay(double value) { return Math.round(value * 2.0) / 2.0; }

    private record WorkSummary(double scheduledWorkingDays, double timesheetPresentDays, double paidLeaveDays,
                               double projectOffDays, double absentDays, double lwpDays, double payableDays) {
        double daysWorked() { return timesheetPresentDays; }
    }

}
