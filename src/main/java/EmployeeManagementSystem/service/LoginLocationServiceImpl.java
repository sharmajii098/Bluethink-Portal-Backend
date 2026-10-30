package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.LoginLocation;
import EmployeeManagementSystem.repository.LoginLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service @RequiredArgsConstructor
public class LoginLocationServiceImpl implements LoginLocationService {
    private final LoginLocationRepository repository;
    public void save(LoginLocation x) { repository.save(x); }
    public List<LoginLocation> getAllLoginLocations() { return repository.findAll(); }
    public List<LoginLocation> getByEmployeeId(String employeeId) { return repository.findByEmployeeIdOrderByLoginTimeDesc(employeeId); }
    public java.util.Optional<LoginLocation> latestForDay(String employeeId, java.time.LocalDate date) { return repository.findFirstByEmployeeIdAndLoginDateOrderByLoginTimeDesc(employeeId, date); }
    public List<LoginLocation> forDay(java.time.LocalDate date) { return repository.findByLoginDateOrderByLoginTimeDesc(date); }
    public List<LoginLocation> forEmployeesOnDay(java.util.Collection<String> ids, java.time.LocalDate date) { return repository.findByEmployeeIdInAndLoginDateOrderByLoginTimeDesc(ids, date); }
    public void delete(Long id) { repository.deleteById(id); }
}
