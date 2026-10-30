package EmployeeManagementSystem.config;

import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.entity.EmployeeProfile;
import EmployeeManagementSystem.entity.RegisterEmployee;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.repository.EmployeeProfileRepository;
import EmployeeManagementSystem.repository.RegisterEmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Creates the initial ADMIN account for local development.
 *
 * The administrator is created only when the configured ADMIN
 * user ID does not already exist.
 */
@Component
@RequiredArgsConstructor
@org.springframework.core.annotation.Order(0)
public class BootstrapAdminRunner implements ApplicationRunner {

    private final RegisterEmployeeRepository registerEmployeeRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeProfileRepository employeeProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${hrms.bootstrap-admin.enabled:false}")
    private boolean enabled;

    @Value("${hrms.bootstrap-admin.user-id:ADMIN001}")
    private String userId;

    @Value("${hrms.bootstrap-admin.password:}")
    private String password;

    @Value("${hrms.bootstrap-admin.name:System Administrator}")
    private String name;

    @Value("${hrms.bootstrap-admin.email:admin@bluethink.local}")
    private String email;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        // =========================================================
        // CHECK WHETHER BOOTSTRAP ADMIN IS ENABLED
        // =========================================================
        if (!enabled) {
            System.out.println("====================================================");
            System.out.println("Bootstrap administrator creation is DISABLED");
            System.out.println("====================================================");
            return;
        }

        // =========================================================
        // CHECK SPECIFIC ADMIN USER ID
        // =========================================================
        RegisterEmployee existingAdmin =
                registerEmployeeRepository.findByUserId(userId);

        if (existingAdmin != null) {

            System.out.println("====================================================");
            System.out.println("Bootstrap administrator already exists");
            System.out.println("User ID : [REDACTED]");
            System.out.println("Role    : " + existingAdmin.getRole());
            System.out.println("====================================================");

            return;
        }

        // =========================================================
        // CREATE LOGIN ACCOUNT
        // =========================================================
        RegisterEmployee account = new RegisterEmployee();

        account.setUserId(userId);
        account.setName(name);
        account.setEmail(email);
        account.setRole("ADMIN");
        account.setDesignation("Administrator");

        // IMPORTANT:
        // Password must be encoded.
        if (!EmployeeManagementSystem.service.PasswordPolicy.valid(password))
            throw new IllegalStateException("Bootstrap admin password must be 12–72 UTF-8 bytes and include letters and numbers.");
        account.setPassword(passwordEncoder.encode(password));

        account = registerEmployeeRepository.save(account);

        // =========================================================
        // CREATE EMPLOYEE RECORD
        // =========================================================
        Employee employee = new Employee();

        employee.setFirstName("System");
        employee.setLastName("Administrator");
        employee.setFullName(name);
        employee.setEmail(email);
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setJoiningDate(LocalDate.now());
        employee.setRegisterEmployee(account);

        employee = employeeRepository.save(employee);

        // =========================================================
        // CREATE EMPLOYEE PROFILE
        // =========================================================
        EmployeeProfile profile = new EmployeeProfile();

        profile.setUserId(userId);
        profile.setFullName(name);
        profile.setEmail(email);
        profile.setDob(LocalDate.of(1990, 1, 1));
        profile.setDesignation("Administrator");
        profile.setStatus("ACTIVE");
        profile.setEmployee(employee);

        employeeProfileRepository.save(profile);

        // =========================================================
        // LOG RESULT
        // =========================================================
        System.out.println();
        System.out.println("====================================================");
        System.out.println("       BOOTSTRAP ADMINISTRATOR CREATED");
        System.out.println("====================================================");
        System.out.println("User ID : [REDACTED]");
        System.out.println("Password: configured privately; never printed.");
        System.out.println("Role    : ADMIN");
        System.out.println("Email   : [REDACTED]");
        System.out.println("====================================================");
        System.out.println();
    }
}