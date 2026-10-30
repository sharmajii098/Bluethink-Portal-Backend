package EmployeeManagementSystem.service;

import EmployeeManagementSystem.dto.LoginRequest;
import EmployeeManagementSystem.entity.RegisterEmployee;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.repository.RegisterEmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegisterEmployeeImpl implements RegisterEmployeeService {
    private final RegisterEmployeeRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public String login(LoginRequest request) {

        RegisterEmployee employee =
                repository.findByUserId(request.getUserId());

        if (employee == null) {
            throw new RuntimeException("Invalid username");
        }

        if (employee.getEmployee() != null && employee.getEmployee().getEmployeeProfile() != null
                && employee.getEmployee().getEmployeeProfile().getStatus() != null
                && !"ACTIVE".equalsIgnoreCase(employee.getEmployee().getEmployeeProfile().getStatus())) {
            throw new RuntimeException("Invalid credentials");
        }
        if (passwordEncoder.matches(
                request.getPassword(),
                employee.getPassword())) {

            return jwtUtil.generateToken(
                    employee.getUserId(),
                    employee.getRole()
            );
        }

        throw new RuntimeException("Invalid password");
    }
    @Override
    public RegisterEmployee save(RegisterEmployee employee) {
        return repository.save(employee);
    }

    public RegisterEmployee getEmployeeById(String employeeId){
        return repository.findByUserId(employeeId);
    }
    @Override
    public List<RegisterEmployee> getUpcomingBirthdays() {
        return repository.findUpcomingBirthdays();
    }
}
