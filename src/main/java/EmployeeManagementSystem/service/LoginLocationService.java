package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.LoginLocation;
import java.util.List;

public interface LoginLocationService {
    void save(LoginLocation loginLocation);
    List<LoginLocation> getAllLoginLocations();
    List<LoginLocation> getByEmployeeId(String employeeId);
    java.util.Optional<LoginLocation> latestForDay(String employeeId, java.time.LocalDate date);
    List<LoginLocation> forDay(java.time.LocalDate date);
    List<LoginLocation> forEmployeesOnDay(java.util.Collection<String> employeeIds, java.time.LocalDate date);
    void delete(Long id);
}
