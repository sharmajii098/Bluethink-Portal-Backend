package EmployeeManagementSystem.repository;

import EmployeeManagementSystem.entity.RegisterEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegisterEmployeeRepository
        extends JpaRepository<RegisterEmployee, Long> {

    RegisterEmployee findByUserId(String userId);
    Optional<RegisterEmployee> findFirstByUserIdIsNotNullOrderByIdDesc();

    Optional<RegisterEmployee> findFirstByOrderByIdDesc();

    Optional<RegisterEmployee> findByEmail(String email);
    Optional<RegisterEmployee> findByEmailIgnoreCase(String email);

    @Query(value = "SELECT r.* FROM register_employee r JOIN employees e ON e.register_employee_id = r.id WHERE DATE_FORMAT(e.date_of_birth, '%m-%d') BETWEEN DATE_FORMAT(CURDATE(), '%m-%d') AND DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 7 DAY), '%m-%d') ORDER BY DATE_FORMAT(e.date_of_birth, '%m-%d') ASC", nativeQuery = true)
    List<RegisterEmployee> findUpcomingBirthdays();

    // ============================================================
    // REPORTING MANAGERS
    // ============================================================

    @Query("""
    SELECT r
    FROM RegisterEmployee r
    WHERE r.departmentId = (
        SELECT d.id
        FROM Department d
        WHERE LOWER(d.departmentName) = LOWER(:department)
    )
    AND LOWER(r.designation) IN (
        'manager',
        'senior manager',
        'team lead',
        'project manager'
    )
    ORDER BY r.name ASC
""")
    List<RegisterEmployee> findReportingManagers(
            @Param("department") String department
    );
}