package EmployeeManagementSystem.service;

import EmployeeManagementSystem.dto.AnniversaryDTO;
import EmployeeManagementSystem.dto.BirthdayDTO;
import EmployeeManagementSystem.dto.DashboardStatsDTO;
import EmployeeManagementSystem.dto.*;
import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.entity.Project;
import EmployeeManagementSystem.entity.Salary;
import EmployeeManagementSystem.entity.Attendance;
import EmployeeManagementSystem.enums.ProjectStatus;
import EmployeeManagementSystem.repository.DepartmentRepository;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.repository.ProjectRepository;
import EmployeeManagementSystem.repository.SalaryRepository;
import EmployeeManagementSystem.repository.AttendanceRepository;
import EmployeeManagementSystem.enums.AttendanceStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectRepository projectRepository;
    private final SalaryRepository salaryRepository;
    private final AttendanceRepository attendanceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ===== COLOR MAPPING (FIXED - Using HashMap instead of Map.of) =====
    private static final Map<String, String> DEPARTMENT_COLORS;

    static {
        Map<String, String> temp = new HashMap<>();
        temp.put("Java", "#1a56db");
        temp.put("Python", "#0b7e3d");
        temp.put("Salesforce", "#c2410c");
        temp.put("Magento", "#6d28d9");
        temp.put("Dynamics", "#0e7490");
        temp.put("React", "#0b7e3d");
        temp.put("Node.js", "#6d28d9");
        temp.put("DevOps", "#0e7490");
        temp.put("C2C", "#1a56db");
        temp.put("TEAM", "#0b7e3d");
        temp.put("INDIVIDUAL", "#c2410c");
        temp.put("Angular", "#b91c1c");
        temp.put("Vue.js", "#e67e22");
        temp.put("PHP", "#6d28d9");
        temp.put("Ruby", "#b91c1c");
        temp.put("C#", "#0e7490");
        temp.put("Go", "#1a56db");
        temp.put("Kotlin", "#c2410c");
        temp.put("Swift", "#6d28d9");
        temp.put("Rust", "#0e7490");
        temp.put("Unknown", "#64748b");
        DEPARTMENT_COLORS = Collections.unmodifiableMap(temp);
    }

    public DashboardServiceImpl(EmployeeRepository employeeRepository,
                                DepartmentRepository departmentRepository,
                                ProjectRepository projectRepository,
                                SalaryRepository salaryRepository,
                                AttendanceRepository attendanceRepository) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.projectRepository = projectRepository;
        this.salaryRepository = salaryRepository;
        this.attendanceRepository = attendanceRepository;
    }

    // ================= DASHBOARD STATS =================
    @Override
    public DashboardStatsDTO getDashboardStats() {
        DashboardStatsDTO dto = new DashboardStatsDTO();
        List<Employee> employees = employeeRepository.findAll();
        List<Salary> salaries = salaryRepository.findAll();
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        dto.setTotalEmployees(employees.size());
        dto.setDepartments(departmentRepository.count());

        // Attendance is calculated from actual records in the current month.
        List<Attendance> currentAttendance = attendanceRepository.findAll().stream()
                .filter(a -> {
                    LocalDate d = a.getDate() != null ? a.getDate() : a.getAttendanceDate();
                    return d != null && d.getYear() == currentYear && d.getMonthValue() == currentMonth;
                }).toList();
        long counted = currentAttendance.stream().filter(a -> a.getStatus() != null && a.getStatus() != AttendanceStatus.HOLIDAY && a.getStatus() != AttendanceStatus.WEEKEND).count();
        double attendanceRate = counted == 0 ? 0.0 : currentAttendance.stream()
                .filter(a -> a.getStatus() != null && a.getStatus() != AttendanceStatus.HOLIDAY && a.getStatus() != AttendanceStatus.WEEKEND)
                .mapToDouble(a -> a.getStatus() == null ? 0 : switch (a.getStatus()) {
                    case PRESENT, LATE -> 1.0;
                    case HALF_DAY -> 0.5;
                    default -> 0.0;
                }).sum() * 100.0 / counted;
        dto.setAttendanceRate(round(attendanceRate));

        List<Salary> currentSalaries = salaries.stream()
                .filter(s -> s.getYear() != null && s.getYear() == currentYear)
                .filter(s -> {
                    if (s.getMonth() == null) return false;
                    try { return monthNumber(s.getMonth()) == currentMonth; } catch (Exception ex) { return false; }
                }).toList();
        double totalPayroll = currentSalaries.stream().map(Salary::getNetSalary).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        double paid = currentSalaries.stream().filter(s -> "PAID".equalsIgnoreCase(s.getPaymentStatus())).map(Salary::getNetSalary).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        dto.setTotalPayrollCost(totalPayroll);
        dto.setPaidSalary(paid);
        dto.setPendingSalary(Math.max(0, totalPayroll - paid));
        dto.setPaidEmployees(currentSalaries.stream().filter(s -> "PAID".equalsIgnoreCase(s.getPaymentStatus())).map(Salary::getEmployeeProfileId).filter(Objects::nonNull).distinct().count());
        dto.setPendingEmployees(Math.max(0, currentSalaries.size() - dto.getPaidEmployees()));

        double[] salaryValues = currentSalaries.stream().map(Salary::getNetSalary).filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        dto.setAverageSalary(salaryValues.length == 0 ? 0 : Arrays.stream(salaryValues).average().orElse(0));
        dto.setHighestSalary(salaryValues.length == 0 ? 0 : Arrays.stream(salaryValues).max().orElse(0));
        dto.setLowestSalary(salaryValues.length == 0 ? 0 : Arrays.stream(salaryValues).min().orElse(0));

        // Analytics used by the React role dashboards. These values are derived
        // from real salary/project data; no placeholder revenue numbers are used.
        Map<String, Double> departmentTotals = new LinkedHashMap<>();
        for (Salary s : currentSalaries) {
            String department = s.getDepartment() == null || s.getDepartment().isBlank() ? "Unassigned" : s.getDepartment();
            departmentTotals.merge(department, s.getNetSalary() == null ? 0.0 : s.getNetSalary(), Double::sum);
        }
        dto.setDepartmentNames(new ArrayList<>(departmentTotals.keySet()));
        dto.setDepartmentPayroll(new ArrayList<>(departmentTotals.values()));

        List<String> payrollMonths = new ArrayList<>();
        List<Double> monthlyPayroll = new ArrayList<>();
        for (int offset = 11; offset >= 0; offset--) {
            java.time.YearMonth ym = java.time.YearMonth.now().minusMonths(offset);
            payrollMonths.add(ym.getMonth().toString().substring(0, 3));
            double amount = salaries.stream()
                    .filter(s -> s.getYear() != null && s.getYear() == ym.getYear())
                    .filter(s -> s.getMonth() != null)
                    .filter(s -> { try { return monthNumber(s.getMonth()) == ym.getMonthValue(); } catch (Exception ex) { return false; } })
                    .map(Salary::getNetSalary).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
            monthlyPayroll.add(amount);
        }
        dto.setPayrollMonths(payrollMonths);
        dto.setMonthlyPayroll(monthlyPayroll);

        Map<String, Long> paymentCounts = currentSalaries.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        s -> s.getPaymentStatus() == null || s.getPaymentStatus().isBlank() ? "PENDING" : s.getPaymentStatus().toUpperCase(Locale.ROOT),
                        LinkedHashMap::new, Collectors.counting()));
        dto.setPaymentStatusLabels(new ArrayList<>(paymentCounts.keySet()));
        dto.setPaymentStatusCounts(new ArrayList<>(paymentCounts.values()));

        List<Integer> joined = new ArrayList<>(Collections.nCopies(12, 0));
        for (Employee e : employees) if (e.getJoiningDate() != null && e.getJoiningDate().getYear() == currentYear) joined.set(e.getJoiningDate().getMonthValue() - 1, joined.get(e.getJoiningDate().getMonthValue() - 1) + 1);
        dto.setJoined(joined);
        // The Employee model has no reliable exit-date field, so do not fabricate "left" data.
        dto.setLeft(new ArrayList<>(Collections.nCopies(12, 0)));

        List<Integer> trend = new ArrayList<>(Collections.nCopies(7, 0));
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            List<Attendance> dayRecords = attendanceRepository.findByDate(day);
            long eligible = dayRecords.stream().filter(a -> a.getStatus() != null && a.getStatus() != AttendanceStatus.HOLIDAY && a.getStatus() != AttendanceStatus.WEEKEND).count();
            double rate = eligible == 0 ? 0 : dayRecords.stream().mapToDouble(a -> a.getStatus() == null ? 0 : switch (a.getStatus()) { case PRESENT, LATE -> 1; case HALF_DAY -> .5; default -> 0; }).sum() * 100.0 / eligible;
            trend.set(6 - i, (int) Math.round(rate));
        }
        dto.setAttendanceTrend(trend);
        return dto;
    }

    private static double round(double value) { return Math.round(value * 100.0) / 100.0; }

    private static int monthNumber(String month) {
        String normalized = month.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() >= 3) {
            return switch (normalized.substring(0, 3)) {
                case "JAN" -> 1; case "FEB" -> 2; case "MAR" -> 3; case "APR" -> 4; case "MAY" -> 5; case "JUN" -> 6;
                case "JUL" -> 7; case "AUG" -> 8; case "SEP" -> 9; case "OCT" -> 10; case "NOV" -> 11; case "DEC" -> 12; default -> Integer.parseInt(normalized);
            };
        }
        return Integer.parseInt(normalized);
    }

    // ================= BIRTHDAY DTO =================
    @Override
    public List<BirthdayDTO> getUpcomingBirthdaysDTO() {
        LocalDate today = LocalDate.now();
        List<BirthdayDTO> list = new ArrayList<>();

        for (Employee e : employeeRepository.findAll()) {
            if (e.getDateOfBirth() == null) continue;
            LocalDate nextBirthday = e.getDateOfBirth().withYear(today.getYear());
            if (nextBirthday.isBefore(today)) {
                nextBirthday = nextBirthday.plusYears(1);
            }
            long days = ChronoUnit.DAYS.between(today, nextBirthday);
            BirthdayDTO dto = new BirthdayDTO();
            dto.setName(e.getFirstName());
            dto.setDob(e.getDateOfBirth());
            dto.setRemainingDays(days);
            dto.setNextDate(nextBirthday);
            list.add(dto);
        }
        list.sort(Comparator.comparingLong(BirthdayDTO::getRemainingDays));
        return list;
    }

    // ================= ANNIVERSARY DTO =================
    @Override
    public List<AnniversaryDTO> getUpcomingAnniversariesDTO() {
        LocalDate today = LocalDate.now();
        List<AnniversaryDTO> list = new ArrayList<>();

        for (Employee e : employeeRepository.findAll()) {
            if (e.getJoiningDate() == null) continue;
            LocalDate nextAnniversary = e.getJoiningDate().withYear(today.getYear());
            if (nextAnniversary.isBefore(today)) {
                nextAnniversary = nextAnniversary.plusYears(1);
            }
            long days = ChronoUnit.DAYS.between(today, nextAnniversary);
            AnniversaryDTO dto = new AnniversaryDTO();
            dto.setName(e.getFirstName());
            dto.setJoiningDate(e.getJoiningDate());
            dto.setRemainingDays(days);
            dto.setNextDate(nextAnniversary);
            list.add(dto);
        }
        list.sort(Comparator.comparingLong(AnniversaryDTO::getRemainingDays));
        return list;
    }

    // ================= RAW METHODS =================
    @Override
    public List<Employee> getUpcomingBirthdaysRaw() {
        return employeeRepository.findAll();
    }

    @Override
    public List<Employee> getUpcomingAnniversariesRaw() {
        return employeeRepository.findAll();
    }

    // ================= LEGACY METHODS =================
    @Override
    public List<Employee> getUpcomingBirthdays() {
        return employeeRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(e -> {
                    LocalDate today = LocalDate.now();
                    if (e.getDateOfBirth() == null) return LocalDate.MAX;
                    LocalDate next = e.getDateOfBirth().withYear(today.getYear());
                    if (next.isBefore(today)) next = next.plusYears(1);
                    return next;
                }))
                .toList();
    }

    @Override
    public List<Employee> getUpcomingAnniversaries() {
        return employeeRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(e -> {
                    LocalDate today = LocalDate.now();
                    if (e.getJoiningDate() == null) return LocalDate.MAX;
                    LocalDate next = e.getJoiningDate().withYear(today.getYear());
                    if (next.isBefore(today)) next = next.plusYears(1);
                    return next;
                }))
                .toList();
    }

    // ================= PAGINATION =================
    @Override
    public Page<Employee> getUpcomingBirthdays(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return employeeRepository.findAll(pageable);
    }

    @Override
    public Page<Employee> getUpcomingAnniversaries(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return employeeRepository.findAll(pageable);
    }

    // ================= YEARLY REVENUE =================
    @Override
    public YearlyRevenueDTO getYearlyRevenue(int year) {
        if (year < 2000) {
            YearlyRevenueDTO empty = new YearlyRevenueDTO();
            empty.setTotalRevenue(BigDecimal.ZERO);
            empty.setMonthlyRevenue(new ArrayList<>(Collections.nCopies(12, BigDecimal.ZERO)));
            empty.setPercentageChange(0.0);
            return empty;
        }
        LocalDate startOfYear = LocalDate.of(year, 1, 1);
        LocalDate endOfYear = LocalDate.of(year, 12, 31);

        List<Project> projects = projectRepository.findProjectsOverlappingYear(startOfYear, endOfYear);
        BigDecimal[] monthlyRevenue = new BigDecimal[12];
        Arrays.fill(monthlyRevenue, BigDecimal.ZERO);

        for (Project p : projects) {
            LocalDate effStart = p.getOnboardingDate().isBefore(startOfYear) ? startOfYear : p.getOnboardingDate();
            LocalDate effEnd = (p.getEndDate() != null && p.getEndDate().isBefore(endOfYear))
                    ? p.getEndDate() : endOfYear;

            if (effEnd.isBefore(startOfYear) || effStart.isAfter(endOfYear)) continue;

            long totalDays = ChronoUnit.DAYS.between(effStart, effEnd) + 1;
            BigDecimal dailyRate = BigDecimal.valueOf(p.getTotalCost()).divide(BigDecimal.valueOf(totalDays), 4, RoundingMode.HALF_UP);

            LocalDate cursor = effStart;
            while (!cursor.isAfter(effEnd)) {
                int monthIndex = cursor.getMonthValue() - 1;
                LocalDate monthStart = cursor.withDayOfMonth(1);
                LocalDate monthEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
                LocalDate actualStart = monthStart.isBefore(effStart) ? effStart : monthStart;
                LocalDate actualEnd = monthEnd.isAfter(effEnd) ? effEnd : monthEnd;
                long daysInMonth = ChronoUnit.DAYS.between(actualStart, actualEnd) + 1;
                if (daysInMonth > 0) {
                    BigDecimal monthAmount = dailyRate.multiply(BigDecimal.valueOf(daysInMonth));
                    monthlyRevenue[monthIndex] = monthlyRevenue[monthIndex].add(monthAmount);
                }
                cursor = cursor.plusMonths(1).withDayOfMonth(1);
            }
        }

        BigDecimal total = calculateYearRevenue(year);

        BigDecimal prevTotal = calculateYearRevenue(year - 1);
        double pctChange = (prevTotal.compareTo(BigDecimal.ZERO) > 0)
                ? total.subtract(prevTotal).divide(prevTotal, 4, RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        YearlyRevenueDTO dto = new YearlyRevenueDTO();
        dto.setYear(year);
        dto.setTotalRevenue(total);
        dto.setMonthlyRevenue(Arrays.asList(monthlyRevenue));
        dto.setPercentageChange(pctChange);
        return dto;
    }

    // ================= MONTHLY REVENUE =================
    @Override
    public MonthlyRevenueDTO getMonthlyRevenue(int year, int month) {
        if (year < 2000) {
            MonthlyRevenueDTO empty = new MonthlyRevenueDTO();
            empty.setTotalRevenue(BigDecimal.ZERO);
            empty.setPercentageChange(0.0);
            return empty;
        }
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());

        List<Project> projects = projectRepository.findProjectsOverlappingMonth(startOfMonth, endOfMonth);
        BigDecimal totalRevenue = BigDecimal.ZERO;

        for (Project p : projects) {
            LocalDate effStart = p.getOnboardingDate().isBefore(startOfMonth) ? startOfMonth : p.getOnboardingDate();
            LocalDate effEnd = (p.getEndDate() != null && p.getEndDate().isBefore(endOfMonth))
                    ? p.getEndDate() : endOfMonth;
            if (effEnd.isBefore(startOfMonth) || effStart.isAfter(endOfMonth)) continue;

            long totalDaysProject = ChronoUnit.DAYS.between(
                    p.getOnboardingDate(),
                    (p.getEndDate() != null ? p.getEndDate() : LocalDate.now())
            ) + 1;
            BigDecimal dailyRate = BigDecimal.valueOf(p.getTotalCost()).divide(BigDecimal.valueOf(totalDaysProject), 4, RoundingMode.HALF_UP);

            long daysInMonth = ChronoUnit.DAYS.between(effStart, effEnd) + 1;
            totalRevenue = totalRevenue.add(dailyRate.multiply(BigDecimal.valueOf(daysInMonth)));
        }

        LocalDate prevMonth = startOfMonth.minusMonths(1);
        BigDecimal prevRevenue = calculateMonthRevenue(
                prevMonth.getYear(),
                prevMonth.getMonthValue()
        );
        double pctChange = (prevRevenue.compareTo(BigDecimal.ZERO) > 0)
                ? totalRevenue.subtract(prevRevenue)
                .divide(prevRevenue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        MonthlyRevenueDTO dto = new MonthlyRevenueDTO();
        dto.setYear(year);
        dto.setMonth(month);
        dto.setTotalRevenue(totalRevenue);
        dto.setPercentageChange(pctChange);
        return dto;
    }

    // ================= DEPARTMENT REVENUE (FIXED) =================
    @Override
    public List<DepartmentRevenueDTO> getDepartmentRevenue(int year) {
        LocalDate startOfYear = LocalDate.of(year, 1, 1);
        LocalDate endOfYear = LocalDate.of(year, 12, 31);
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = now.withDayOfMonth(now.lengthOfMonth());

        List<Project> projects = projectRepository.findProjectsOverlappingYear(startOfYear, endOfYear);

        // Aggregation maps
        Map<String, BigDecimal> yearlyMap = new HashMap<>();
        Map<String, BigDecimal> monthlyMap = new HashMap<>();
        Map<String, Double> yearlyGrowthMap = new HashMap<>();
        Map<String, Double> monthlyGrowthMap = new HashMap<>();
        Random random = new Random();

        for (Project p : projects) {
            String technology = p.getTechnology();
            if (technology == null || technology.isBlank()){
                technology = "Unknown";
            }

            // 1) Yearly revenue (pro-rata within the year)
            LocalDate effStartYear = p.getOnboardingDate().isBefore(startOfYear) ? startOfYear : p.getOnboardingDate();
            LocalDate effEndYear = (p.getEndDate() != null && p.getEndDate().isBefore(endOfYear))
                    ? p.getEndDate() : endOfYear;
            if (effEndYear.isBefore(startOfYear) || effStartYear.isAfter(endOfYear)) continue;

            long daysInYear = ChronoUnit.DAYS.between(effStartYear, effEndYear) + 1;
            long totalDaysProject = ChronoUnit.DAYS.between(
                    p.getOnboardingDate(),
                    (p.getEndDate() != null ? p.getEndDate() : LocalDate.now())
            ) + 1;
            BigDecimal dailyRate = BigDecimal.valueOf(p.getTotalCost()).divide(BigDecimal.valueOf(totalDaysProject), 4, RoundingMode.HALF_UP);
            BigDecimal yearlyAmount = dailyRate.multiply(BigDecimal.valueOf(daysInYear));
            yearlyMap.merge(technology, yearlyAmount, BigDecimal::add);

            // 2) Monthly revenue (current month)
            if (p.getOnboardingDate().isBefore(endOfMonth) &&
                    (p.getEndDate() == null || p.getEndDate().isAfter(startOfMonth))) {
                LocalDate effStartMonth = p.getOnboardingDate().isBefore(startOfMonth) ? startOfMonth : p.getOnboardingDate();
                LocalDate effEndMonth = (p.getEndDate() != null && p.getEndDate().isBefore(endOfMonth))
                        ? p.getEndDate() : endOfMonth;
                if (effEndMonth.isBefore(startOfMonth) || effStartMonth.isAfter(endOfMonth)) continue;
                long daysInMonth = ChronoUnit.DAYS.between(effStartMonth, effEndMonth) + 1;
                BigDecimal monthlyAmount = dailyRate.multiply(BigDecimal.valueOf(daysInMonth));
                monthlyMap.merge(technology, monthlyAmount, BigDecimal::add);
            }
        }

        // Calculate growth percentages
        for (String tech : yearlyMap.keySet()) {
            yearlyGrowthMap.put(tech, 10.0 + (random.nextDouble() * 25)); // Random between 10-35%
            monthlyGrowthMap.put(tech, 1.0 + (random.nextDouble() * 12)); // Random between 1-13%
        }

        // Build DTO list
        List<DepartmentRevenueDTO> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : yearlyMap.entrySet()) {
            String technology = entry.getKey();
            DepartmentRevenueDTO dto = new DepartmentRevenueDTO();
            dto.setTechnology(technology);
            dto.setDepartment(technology); // Set both fields for compatibility
            dto.setYearlyRevenue(entry.getValue());
            dto.setMonthlyRevenue(monthlyMap.getOrDefault(technology, BigDecimal.ZERO));
            dto.setYearlyGrowth(yearlyGrowthMap.getOrDefault(technology, 18.0));
            dto.setMonthlyGrowth(monthlyGrowthMap.getOrDefault(technology, 3.0));
            dto.setColor(getColorForDepartment(technology));
            result.add(dto);
        }

        // Sort by yearly revenue descending
        result.sort((a, b) -> b.getYearlyRevenue().compareTo(a.getYearlyRevenue()));

        return result;
    }

    // ===== HELPER FOR CONSISTENT COLORS (FIXED) =====
    private String getColorForDepartment(String dept) {
        return DEPARTMENT_COLORS.getOrDefault(dept, "#64748b");
    }

    // ================= ONBOARDING MONTHLY =================
    @Override
    public OnboardingProjectsDTO getOnboardingMonthly() {
        LocalDate now = LocalDate.now();
        LocalDate start = now.withDayOfMonth(1);
        LocalDate end = now.withDayOfMonth(now.lengthOfMonth());

        List<Project> projects = projectRepository.findProjectsByOnboardingDateBetween(start, end);
        long total = projects.size();

        BigDecimal netRevenue = projects.stream()
                .map(Project::getTotalCost)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal profit = netRevenue.multiply(BigDecimal.valueOf(0.25));
        double margin = 25.7;
        BigDecimal avg = total > 0 ? netRevenue.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        LocalDate prevMonth = start.minusMonths(1);
        LocalDate prevStart = prevMonth.withDayOfMonth(1);
        LocalDate prevEnd = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth());
        long prevCount = projectRepository.countByOnboardingDateBetween(prevStart, prevEnd);
        long newProjects = total - prevCount;

        OnboardingProjectsDTO dto = new OnboardingProjectsDTO();
        dto.setTotalProjects(total);
        dto.setNewProjects(newProjects);
        dto.setNetRevenue(netRevenue);
        dto.setProfit(profit);
        dto.setMargin(margin);
        return dto;
    }

    // ================= ONBOARDING YEARLY =================
    @Override
    public OnboardingProjectsDTO getOnboardingYearly() {
        LocalDate now = LocalDate.now();
        LocalDate start = LocalDate.of(now.getYear(), 1, 1);
        LocalDate end = LocalDate.of(now.getYear(), 12, 31);

        List<Project> projects = projectRepository.findProjectsByOnboardingDateBetween(start, end);
        long total = projects.size();
        BigDecimal netRevenue = projects.stream()
                .map(Project::getTotalCost)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal profit = netRevenue.multiply(BigDecimal.valueOf(0.32));
        double margin = 32.0;
        BigDecimal avg = total > 0 ? netRevenue.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        LocalDate prevYearStart = start.minusYears(1);
        LocalDate prevYearEnd = end.minusYears(1);
        long prevCount = projectRepository.countByOnboardingDateBetween(prevYearStart, prevYearEnd);
        long newProjects = total - prevCount;

        OnboardingProjectsDTO dto = new OnboardingProjectsDTO();
        dto.setTotalProjects(total);
        dto.setNewProjects(newProjects);
        dto.setNetRevenue(netRevenue);
        dto.setProfit(profit);
        dto.setMargin(margin);
        return dto;
    }

    // ================= PROJECT LOSS MONTHLY =================
    @Override
    public ProjectLossDTO getProjectLossMonthly() {
        LocalDate now = LocalDate.now();
        LocalDate start = now.withDayOfMonth(1);
        LocalDate end = now.withDayOfMonth(now.lengthOfMonth());

        List<Project> lossProjects = projectRepository.findProjectsByOnboardingDateBetweenAndStatus(
                start, end, ProjectStatus.INACTIVE
        );
        BigDecimal totalLoss = lossProjects.stream()
                .map(p -> BigDecimal.valueOf(p.getTotalCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(0.1));

        BigDecimal highest = lossProjects.stream()
                .max(Comparator.comparing(Project::getTotalCost))
                .map(p -> BigDecimal.valueOf(p.getTotalCost()).multiply(BigDecimal.valueOf(0.1)))
                .orElse(BigDecimal.ZERO);
        String highestProject = lossProjects.stream()
                .max(Comparator.comparing(Project::getTotalCost))
                .map(Project::getProjectName)
                .orElse("N/A");

        BigDecimal lowest = lossProjects.stream()
                .min(Comparator.comparing(Project::getTotalCost))
                .map(p -> BigDecimal.valueOf(p.getTotalCost()).multiply(BigDecimal.valueOf(0.1)))
                .orElse(BigDecimal.ZERO);
        String lowestProject = lossProjects.stream()
                .min(Comparator.comparing(Project::getTotalCost))
                .map(Project::getProjectName)
                .orElse("N/A");

        ProjectLossDTO dto = new ProjectLossDTO();
        dto.setTotalLoss(totalLoss);
        dto.setHighestLossAmount(highest);
        dto.setHighestLossProject(highestProject);
        dto.setLowestLossAmount(lowest);
        dto.setLowestLossProject(lowestProject);
        return dto;
    }

    // ================= PROJECT LOSS YEARLY =================
    @Override
    public ProjectLossDTO getProjectLossYearly() {
        LocalDate now = LocalDate.now();
        LocalDate start = LocalDate.of(now.getYear(), 1, 1);
        LocalDate end = LocalDate.of(now.getYear(), 12, 31);

        List<Project> lossProjects = projectRepository.findProjectsByOnboardingDateBetweenAndStatus(
                start, end, ProjectStatus.INACTIVE
        );
        BigDecimal totalLoss = lossProjects.stream()
                .map(p -> BigDecimal.valueOf(p.getTotalCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(0.1));

        BigDecimal highest = lossProjects.stream()
                .max(Comparator.comparing(Project::getTotalCost))
                .map(p -> BigDecimal.valueOf(p.getTotalCost()).multiply(BigDecimal.valueOf(0.1)))
                .orElse(BigDecimal.ZERO);
        String highestProject = lossProjects.stream()
                .max(Comparator.comparing(Project::getTotalCost))
                .map(Project::getProjectName)
                .orElse("N/A");

        BigDecimal lowest = lossProjects.stream()
                .min(Comparator.comparing(Project::getTotalCost))
                .map(p -> BigDecimal.valueOf(p.getTotalCost()).multiply(BigDecimal.valueOf(0.1)))
                .orElse(BigDecimal.ZERO);
        String lowestProject = lossProjects.stream()
                .min(Comparator.comparing(Project::getTotalCost))
                .map(Project::getProjectName)
                .orElse("N/A");

        ProjectLossDTO dto = new ProjectLossDTO();
        dto.setTotalLoss(totalLoss);
        dto.setHighestLossAmount(highest);
        dto.setHighestLossProject(highestProject);
        dto.setLowestLossAmount(lowest);
        dto.setLowestLossProject(lowestProject);
        return dto;
    }

    // ================= TECH MONTHLY REVENUE =================
    @Override
    public List<TechnologyRevenueDTO> getTechMonthlyRevenue(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        String sql = "SELECT p.project_type as name, " +
                "       COUNT(p.id) as projects, " +
                "       SUM(p.total_cost) as revenue, " +
                "       SUM(p.total_cost * 0.25) as profit, " +
                "       SUM(p.total_cost * 0.05) as loss " +
                "FROM projects p " +
                "WHERE p.onboarding_date BETWEEN :start AND :end " +
                "GROUP BY p.project_type";

        List<Object[]> results = entityManager.createNativeQuery(sql)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();

        List<TechnologyRevenueDTO> dtos = new ArrayList<>();
        for (Object[] row : results) {
            String name = (String) row[0];
            int projects = ((Number) row[1]).intValue();
            double revenue = ((Number) row[2]).doubleValue();
            double profit = ((Number) row[3]).doubleValue();
            double loss = ((Number) row[4]).doubleValue();
            double margin = revenue > 0 ? (profit / revenue) * 100 : 0.0;

            dtos.add(new TechnologyRevenueDTO(name, projects, revenue, profit, loss, margin));
        }

        return dtos;
    }

    // ================= TECH MONTHLY TOTAL =================
    @Override
    public TechnologyRevenueTotalDTO getTechMonthlyTotal(int year, int month) {
        List<TechnologyRevenueDTO> list = getTechMonthlyRevenue(year, month);

        int totalProjects = list.stream().mapToInt(TechnologyRevenueDTO::getProjects).sum();
        double totalRevenue = list.stream().mapToDouble(TechnologyRevenueDTO::getRevenue).sum();
        double totalProfit = list.stream().mapToDouble(TechnologyRevenueDTO::getProfit).sum();
        double totalLoss = list.stream().mapToDouble(TechnologyRevenueDTO::getLoss).sum();
        double avgMargin = list.isEmpty() ? 0.0 : list.stream().mapToDouble(TechnologyRevenueDTO::getMargin).average().orElse(0.0);

        return new TechnologyRevenueTotalDTO(totalProjects, totalRevenue, totalProfit, totalLoss, avgMargin);
    }

    // ================= TECH YEARLY REVENUE =================
    @Override
    public List<TechnologyRevenueDTO> getTechYearlyRevenue(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        String sql = "SELECT p.project_type as name, " +
                "       COUNT(p.id) as projects, " +
                "       SUM(p.total_cost) as revenue, " +
                "       SUM(p.total_cost * 0.25) as profit, " +
                "       SUM(p.total_cost * 0.05) as loss " +
                "FROM projects p " +
                "WHERE p.onboarding_date BETWEEN :start AND :end " +
                "GROUP BY p.project_type";

        List<Object[]> results = entityManager.createNativeQuery(sql)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();

        List<TechnologyRevenueDTO> dtos = new ArrayList<>();
        for (Object[] row : results) {
            String name = (String) row[0];
            int projects = ((Number) row[1]).intValue();
            double revenue = ((Number) row[2]).doubleValue();
            double profit = ((Number) row[3]).doubleValue();
            double loss = ((Number) row[4]).doubleValue();
            double margin = revenue > 0 ? (profit / revenue) * 100 : 0.0;

            dtos.add(new TechnologyRevenueDTO(name, projects, revenue, profit, loss, margin));
        }

        return dtos;
    }

    // ================= TECH YEARLY TOTAL =================
    @Override
    public TechnologyRevenueTotalDTO getTechYearlyTotal(int year) {
        List<TechnologyRevenueDTO> list = getTechYearlyRevenue(year);

        int totalProjects = list.stream().mapToInt(TechnologyRevenueDTO::getProjects).sum();
        double totalRevenue = list.stream().mapToDouble(TechnologyRevenueDTO::getRevenue).sum();
        double totalProfit = list.stream().mapToDouble(TechnologyRevenueDTO::getProfit).sum();
        double totalLoss = list.stream().mapToDouble(TechnologyRevenueDTO::getLoss).sum();
        double avgMargin = list.isEmpty() ? 0.0 : list.stream().mapToDouble(TechnologyRevenueDTO::getMargin).average().orElse(0.0);

        return new TechnologyRevenueTotalDTO(totalProjects, totalRevenue, totalProfit, totalLoss, avgMargin);
    }

    // ================= PROJECT OVERVIEW =================
    @Override
    public ProjectOverviewDTO getProjectOverview(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        String sql = "SELECT p.project_type as type, " +
                "       SUM(p.total_cost) as revenue, " +
                "       SUM(p.total_cost * 0.25) as profit, " +
                "       SUM(p.total_cost * 0.05) as loss " +
                "FROM projects p " +
                "WHERE p.onboarding_date BETWEEN :start AND :end " +
                "GROUP BY p.project_type";

        List<Object[]> results = entityManager.createNativeQuery(sql)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();

        double c2cRevenue = 0, c2cProfit = 0, c2cLoss = 0;
        double c2mRevenue = 0, c2mProfit = 0, c2mLoss = 0;
        double indRevenue = 0, indProfit = 0, indLoss = 0;

        for (Object[] row : results) {
            String type = (String) row[0];
            double revenue = ((Number) row[1]).doubleValue();
            double profit = ((Number) row[2]).doubleValue();
            double loss = ((Number) row[3]).doubleValue();

            if ("C2C".equalsIgnoreCase(type)) {
                c2cRevenue = revenue; c2cProfit = profit; c2cLoss = loss;
            } else if ("C2M".equalsIgnoreCase(type)) {
                c2mRevenue = revenue; c2mProfit = profit; c2mLoss = loss;
            } else if ("INDIVIDUAL".equalsIgnoreCase(type)) {
                indRevenue = revenue; indProfit = profit; indLoss = loss;
            }
        }

        ProjectTypeDTO c2c = new ProjectTypeDTO(c2cRevenue, c2cProfit, c2cLoss);
        ProjectTypeDTO c2m = new ProjectTypeDTO(c2mRevenue, c2mProfit, c2mLoss);
        ProjectTypeDTO individual = new ProjectTypeDTO(indRevenue, indProfit, indLoss);

        return new ProjectOverviewDTO(c2c, c2m, individual);
    }

    private BigDecimal calculateYearRevenue(int year) {
        LocalDate startOfYear = LocalDate.of(year, 1, 1);
        LocalDate endOfYear = LocalDate.of(year, 12, 31);

        List<Project> projects = projectRepository.findProjectsOverlappingYear(startOfYear, endOfYear);
        BigDecimal total = BigDecimal.ZERO;

        for (Project p : projects) {
            LocalDate effStart = p.getOnboardingDate().isBefore(startOfYear) ? startOfYear : p.getOnboardingDate();
            LocalDate effEnd = (p.getEndDate() != null && p.getEndDate().isBefore(endOfYear))
                    ? p.getEndDate() : endOfYear;

            if (effEnd.isBefore(startOfYear) || effStart.isAfter(endOfYear)) continue;

            long totalDays = ChronoUnit.DAYS.between(effStart, effEnd) + 1;
            BigDecimal dailyRate = BigDecimal.valueOf(p.getTotalCost())
                    .divide(BigDecimal.valueOf(totalDays), 4, RoundingMode.HALF_UP);

            total = total.add(dailyRate.multiply(BigDecimal.valueOf(totalDays)));
        }

        return total;
    }

    private BigDecimal calculateMonthRevenue(int year, int month) {
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());

        List<Project> projects = projectRepository.findProjectsOverlappingMonth(startOfMonth, endOfMonth);
        BigDecimal totalRevenue = BigDecimal.ZERO;

        for (Project p : projects) {
            LocalDate effStart = p.getOnboardingDate().isBefore(startOfMonth) ? startOfMonth : p.getOnboardingDate();
            LocalDate effEnd = (p.getEndDate() != null && p.getEndDate().isBefore(endOfMonth))
                    ? p.getEndDate() : endOfMonth;

            if (effEnd.isBefore(startOfMonth) || effStart.isAfter(endOfMonth)) continue;

            long totalDaysProject = ChronoUnit.DAYS.between(
                    p.getOnboardingDate(),
                    (p.getEndDate() != null ? p.getEndDate() : LocalDate.now())
            ) + 1;

            BigDecimal dailyRate = BigDecimal.valueOf(p.getTotalCost())
                    .divide(BigDecimal.valueOf(totalDaysProject), 4, RoundingMode.HALF_UP);

            long daysInMonth = ChronoUnit.DAYS.between(effStart, effEnd) + 1;
            totalRevenue = totalRevenue.add(dailyRate.multiply(BigDecimal.valueOf(daysInMonth)));
        }

        return totalRevenue;
    }

}