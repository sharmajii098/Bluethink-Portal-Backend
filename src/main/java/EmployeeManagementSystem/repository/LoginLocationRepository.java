package EmployeeManagementSystem.repository;

import EmployeeManagementSystem.entity.LoginLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LoginLocationRepository extends JpaRepository<LoginLocation, Long> {
    List<LoginLocation> findByEmployeeIdOrderByLoginTimeDesc(String employeeId);
    java.util.Optional<LoginLocation> findFirstByEmployeeIdAndLoginDateOrderByLoginTimeDesc(String employeeId, java.time.LocalDate date);
    List<LoginLocation> findByLoginDateOrderByLoginTimeDesc(java.time.LocalDate date);
    List<LoginLocation> findByEmployeeIdInAndLoginDateOrderByLoginTimeDesc(java.util.Collection<String> employeeIds, java.time.LocalDate date);
}
