package EmployeeManagementSystem.service;

import EmployeeManagementSystem.dto.AnniversaryDTO;
import EmployeeManagementSystem.dto.BirthdayDTO;
import EmployeeManagementSystem.dto.CelebrationDto;
import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.entity.Salary;
import EmployeeManagementSystem.messaging.kafka.EmployeeEvent;
import EmployeeManagementSystem.messaging.kafka.EmployeeProducer;
import EmployeeManagementSystem.repository.EmployeeProfileRepository;
import EmployeeManagementSystem.repository.EmployeeRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeProducer employeeProducer;


    // =========================
    // SAVE EMPLOYEE
    // =========================
    private final EmployeeProfileRepository employeeProfileRepository;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository, EmployeeProducer employeeProducer,
                               EmployeeProfileRepository employeeProfileRepository) {
        this.employeeRepository = employeeRepository;
        this.employeeProducer = employeeProducer;
        this.employeeProfileRepository = employeeProfileRepository;
    }

    @Override
    @Transactional
    public Employee saveEmployee(Employee employee) {
        if (employee == null) {
            throw new IllegalArgumentException("Employee is required");
        }

        // A new employee must be saved even if salary or attendance lists are null.
        if (employee.getSalaries() != null) {
            employee.getSalaries().forEach(salary -> salary.setEmployee(employee));
        }
        if (employee.getAttendanceList() != null) {
            employee.getAttendanceList().forEach(attendance -> attendance.setEmployee(employee));
        }

        Employee savedEmployee = employeeRepository.save(employee);
        String employeeName = savedEmployee.getFullName();
        if (employeeName == null || employeeName.isBlank()) {
            employeeName = (java.util.Objects.toString(savedEmployee.getFirstName(), "") + " "
                    + java.util.Objects.toString(savedEmployee.getLastName(), "")).trim();
        }
        if (employeeName.isBlank()) {
            employeeName = "Employee";
        }

        // This is a legacy service path; normal Admin/HR registration separately
        // sends a post-commit activation invitation. Do not wire both emails together.
        String email = savedEmployee.getEmail();
        if (savedEmployee.getId() != null && email != null && !email.isBlank()) {
            EmployeeEvent event = new EmployeeEvent(savedEmployee.getId(), employeeName, email);
            // Do not publish before the database transaction commits. A best-effort
            // callback is NOT a durable outbox; failures must be monitored/retried.
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        employeeProducer.sendEmployeeCreatedEvent(event);
                    }
                });
            } else {
                employeeProducer.sendEmployeeCreatedEvent(event);
            }
        }
        return savedEmployee;
    }

    // =========================
    // FIND BY DEPARTMENT
    // =========================

    @Override
    public List<Employee> getEmployeesByDepartment(Long departmentId) {

        return employeeRepository.findByDepartmentId(departmentId);
    }


    // =========================
    // FIND ALL
    // =========================

    @Override
    public Object findAll() {

        return employeeRepository.findAll();
    }


    // =========================
    // UPDATE EMPLOYEE
    // =========================

    @Override
    public Employee updateEmployee(Long id, Employee employee) {


        Employee existing = employeeRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found"));


        existing.setFirstName(employee.getFirstName());
        existing.setLastName(employee.getLastName());
        existing.setEmail(employee.getEmail());
        existing.setPhone(employee.getPhone());
        existing.setJoiningDate(employee.getJoiningDate());
        existing.setDepartment(employee.getDepartment());


        // Update Salary List

        if (employee.getSalaries() != null) {


            existing.getSalaries().clear();


            employee.getSalaries()
                    .forEach(salary -> {

                        salary.setEmployee(existing);

                        existing.getSalaries()
                                .add(salary);

                    });
        }


        // Update Attendance List

        if (employee.getAttendanceList() != null) {


            existing.getAttendanceList().clear();


            employee.getAttendanceList()
                    .forEach(attendance -> {

                        attendance.setEmployee(existing);

                        existing.getAttendanceList()
                                .add(attendance);

                    });

        }


        return employeeRepository.save(existing);
    }


    @Override
    public void deleteEmployee(Long id) {


        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found"));


        employeeRepository.delete(employee);

    }


    // =========================
    // GET EMPLOYEE BY ID
    // =========================

    @Override
    public Employee getEmployeeById(Long id) {


        return employeeRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found"));

    }


    // =========================
    // ALL EMPLOYEES LIST
    // =========================

    @Override
    public List<Employee> getAllEmployeesList() {

        return Collections.singletonList((Employee) employeeRepository.findAll());

    }


    // =========================
    // PAGINATION
    // =========================

    @Override
    public Page<Employee> getAllEmployees(
            int pageNo,
            int pageSize,
            String sortBy) {


        Pageable pageable = PageRequest.of(
                pageNo,
                pageSize,
                Sort.by(
                        sortBy == null || sortBy.isBlank()
                                ? "id"
                                : sortBy
                ).ascending()
        );


        return employeeRepository.findAll(pageable);

    }


    // =========================
    // SEARCH EMPLOYEE
    // =========================

    @Override
    public Page<Employee> searchEmployee(
            String keyword,
            Pageable pageable) {


        return employeeRepository.searchAll(
                keyword,
                pageable
        );

    }


    // =========================
    // FIND BY EMAIL
    // =========================

    @Override
    public Employee findByEmail(String email) {


        return employeeRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found"));

    }


    @Override
    public long totalEmployees() {

        return employeeRepository.count();

    }


    @Override
    public List<Employee> getAllEmployees() {

        return employeeRepository.findAll();

    }


    @Override
    public List<Employee> getEmployeesByIds(List<Long> ids) {


        return employeeRepository.findAllById(ids);

    }


    // =========================
    // UPCOMING BIRTHDAYS
    // =========================

    @Override
    public List<BirthdayDTO> getUpcomingBirthdays() {
        LocalDate today = LocalDate.now();

        return employeeProfileRepository.findAll()
                .stream()
                .filter(emp -> emp.getDob() != null)
                .map(emp -> {
                    LocalDate nextBirthday = emp.getDob().withYear(today.getYear());

                    if (nextBirthday.isBefore(today)) {
                        nextBirthday = nextBirthday.plusYears(1);
                    }

                    long days = ChronoUnit.DAYS.between(today, nextBirthday);

                    BirthdayDTO dto = new BirthdayDTO();
                    dto.setName(emp.getFullName());          // fullName use karo
                    dto.setDob(emp.getDob());                // getDob() use karo
                    dto.setDepartment(emp.getDepartment());  // department String hai
                    dto.setRemainingDays(days);
                    dto.setNextDate(nextBirthday);

                    return dto;
                })
                .sorted(Comparator.comparingLong(BirthdayDTO::getRemainingDays))
                .limit(5)
                .toList();
    }

    // =========================
    // UPCOMING ANNIVERSARIES
    // =========================

    @Override
    public List<AnniversaryDTO> getUpcomingAnniversaries() {
        LocalDate today = LocalDate.now();

        return employeeProfileRepository.findAll()
                .stream()
                .filter(emp -> emp.getRegisteredAt() != null)
                .map(emp -> {
                    LocalDate nextAnniversary = LocalDate.from(emp.getRegisteredAt().withYear(today.getYear()));
                    if (nextAnniversary.isBefore(today)) {
                        nextAnniversary = nextAnniversary.plusYears(1);
                    }
                    long days = ChronoUnit.DAYS.between(today, nextAnniversary);

                    AnniversaryDTO dto = new AnniversaryDTO();
                    dto.setName(emp.getFullName());
                    dto.setJoiningDate(LocalDate.from(emp.getRegisteredAt()));
                    dto.setRemainingDays(days);
                    dto.setNextDate(nextAnniversary);
                    return dto;
                })
                .sorted(Comparator.comparingLong(AnniversaryDTO::getRemainingDays))
                .limit(5)
                .toList();
    }
}


//    @Override
//    public Employee updateEmployee(Long id, Employee employee) {
//        return null;
//    }
//
//    @Override
//    public void deleteEmployee(Long id) {
//
//    }
//
//    @Override
//    public Employee getEmployeeById(Long id) {
//        return null;
//    }
//
//    @Override
//    public Page<Employee> getAllEmployees(int pageNo, int pageSize, String sortBy) {
//        return null;
//    }
//
//    @Override
//    public Page<Employee> searchEmployee(String keyword, Pageable pageable) {
//        return null;
//    }
//
//    @Override
//    public List<Employee> getAllEmployeesList() {
//        return List.of();
//    }
//
//    @Override
//    public Employee findByEmail(String email) {
//        return null;
//    }
//
//    @Override
//    public long totalEmployees() {
//        return 0;
//    }
//
//    @Override
//    public List<BirthdayDTO> getUpcomingBirthdays() {
//        return List.of();
//    }
//
//    @Override
//    public List<AnniversaryDTO> getUpcomingAnniversaries() {
//        return List.of();
//    }
//
//    @Override
//    public List<Employee> getAllEmployees() {
//        return List.of();
//    }
//
//    @Override
//    public List<Employee> getEmployeesByIds(List<Long> ids) {
//        return List.of();
//    }
//
//    @Override
//    public List<Employee> getEmployeesByDepartment(Long departmentId) {
//        return List.of();
//    }
//
//    @Override
//    public @Nullable Object findAll() {
//        return null;
//    }
