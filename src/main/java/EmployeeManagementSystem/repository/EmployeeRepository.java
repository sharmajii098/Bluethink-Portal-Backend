package EmployeeManagementSystem.repository;

import EmployeeManagementSystem.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> lockById(@Param("id") Long id);


    // ============================================================
    // SEARCH EMPLOYEES
    // ============================================================

    @Query("""
            SELECT e
            FROM Employee e
            LEFT JOIN e.department d
            WHERE
                LOWER(CONCAT(e.firstName, ' ', e.lastName))
                    LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(e.email)
                    LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(e.phone)
                    LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(d.departmentName, ''))
                    LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    Page<Employee> searchAll(
            @Param("keyword") String keyword,
            Pageable pageable
    );


    // ============================================================
    // SEARCH BY FULL NAME
    // ============================================================

    @Query("""
            SELECT e
            FROM Employee e
            WHERE LOWER(CONCAT(e.firstName, ' ', e.lastName))
                LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    List<Employee> searchByName(
            @Param("keyword") String keyword
    );


    // ============================================================
    // FIND BY EMAIL
    // ============================================================

    Optional<Employee> findByEmail(String email);

    Optional<Employee> findByEmailIgnoreCase(String email);


    // ============================================================
    // FIND BY REGISTER EMPLOYEE USER ID
    // ============================================================

    Optional<Employee> findByRegisterEmployee_UserId(
            String userId
    );

    // Legacy-safe identity lookup: some older records are linked through
    // EmployeeProfile.userId even when register_employee_id is not populated.
    Optional<Employee> findByEmployeeProfile_UserId(String userId);


    // ============================================================
    // FIND BY REPORTING MANAGER ID
    // ============================================================

    List<Employee> findByReportingManager_Id(
            Long reportingManagerId
    );


    // ============================================================
    // FIND BY DEPARTMENT ID
    // ============================================================

    List<Employee> findByDepartmentId(
            Long departmentId
    );


    // ============================================================
    // EMPLOYEE DETAILS
    // ============================================================

    @Query("""
            SELECT DISTINCT e
            FROM Employee e
            LEFT JOIN FETCH e.employeeProfile p
            LEFT JOIN FETCH e.department d
            LEFT JOIN FETCH e.salaries s
            WHERE e.id = :id
            """)
    Optional<Employee> findByIdWithDetails(
            @Param("id") Long id
    );


    // ============================================================
    // SEARCH EMPLOYEES WITH FILTERS
    // ============================================================

    @Query("""
            SELECT DISTINCT e
            FROM Employee e
            LEFT JOIN e.employeeProfile p
            LEFT JOIN e.department d
            WHERE
                (
                    :keyword IS NULL
                    OR LOWER(e.fullName)
                        LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(e.email)
                        LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(p.userId)
                        LIKE LOWER(CONCAT('%', :keyword, '%'))
                )
                AND (
                    :departmentId IS NULL
                    OR e.department.id = :departmentId
                )
                AND (
                    :status IS NULL
                    OR p.status = :status
                )
            """)
    Page<Employee> searchEmployees(
            @Param("keyword") String keyword,
            @Param("departmentId") Long departmentId,
            @Param("status") String status,
            Pageable pageable
    );


    // ============================================================
    // ALL ACTIVE EMPLOYEES
    // ============================================================

    @Query("""
            SELECT DISTINCT e
            FROM Employee e
            LEFT JOIN e.employeeProfile p
            WHERE p.status = 'ACTIVE'
            """)
    List<Employee> findAllActiveEmployees();


    // ============================================================
    // GET USER IDS BY DEPARTMENT NAME
    //
    // Used by HR activity/session/location monitoring.
    // ============================================================

    @Query("""
            SELECT DISTINCT re.userId
            FROM Employee e
            JOIN e.registerEmployee re
            LEFT JOIN e.department d
            WHERE LOWER(d.departmentName) = LOWER(:department)
            """)
    List<String> findUserIdsByDepartment(
            @Param("department") String department
    );


    // ============================================================
    // GET USER IDS REPORTING TO A MANAGER
    //
    // Used by Manager activity/session/location monitoring.
    // ============================================================

    @Query("""
            SELECT DISTINCT re.userId
            FROM Employee e
            JOIN e.registerEmployee re
            JOIN e.reportingManager manager
            JOIN manager.registerEmployee managerRe
            WHERE managerRe.userId = :reportingManagerUserId
            """)
    List<String> findUserIdsByReportingManager(
            @Param("reportingManagerUserId")
            String reportingManagerUserId
    );
}