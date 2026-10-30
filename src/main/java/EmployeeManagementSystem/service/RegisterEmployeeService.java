package EmployeeManagementSystem.service;

import EmployeeManagementSystem.dto.LoginRequest;
import EmployeeManagementSystem.entity.RegisterEmployee;

import java.util.List;

public interface RegisterEmployeeService {
    String login(LoginRequest request);
    RegisterEmployee getEmployeeById(String employeeId);

    RegisterEmployee save(RegisterEmployee employee);
    List<RegisterEmployee> getUpcomingBirthdays();
}
