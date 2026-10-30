package EmployeeManagementSystem.repository;


import EmployeeManagementSystem.entity.Attendance;
import EmployeeManagementSystem.entity.AttendanceTracking;
import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.enums.AttendanceStatus;
import EmployeeManagementSystem.enums.WorkMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository
        extends JpaRepository<Attendance, Long> {

    List<Attendance> findByEmployee(
            Employee employee);

    // Uses the primary date, falling back to attendanceDate for legacy records.
    // Filtering happens in the database rather than loading all history into the AI service.
    @Query("SELECT a FROM Attendance a WHERE a.employee = :employee " +
           "AND COALESCE(a.date, a.attendanceDate) BETWEEN :fromDate AND :toDate")
    List<Attendance> findByEmployeeAndAiDateRange(@Param("employee") Employee employee,
                                                   @Param("fromDate") LocalDate fromDate,
                                                   @Param("toDate") LocalDate toDate);


    List<Attendance> findByCheckInTimeBeforeAndCheckOutTimeIsNull(LocalDateTime cutoff);

    @Query("SELECT a FROM Attendance a JOIN a.employee e JOIN e.registerEmployee u " +
            "WHERE u.userId = :userId AND a.date BETWEEN :fromDate AND :toDate")
    List<Attendance> findCalendarRange(@Param("userId") String userId,
                                       @Param("fromDate") LocalDate fromDate,
                                       @Param("toDate") LocalDate toDate);

    List<Attendance> findByDate(
            LocalDate date);

    Attendance findByEmployeeAndDate(
            Employee employee,
            LocalDate date);
    List<Attendance> findByAttendanceDate(LocalDate attendanceDate);
    List<Attendance> findByAttendanceDateAndWorkMode(LocalDate attendanceDate, WorkMode workMode);

    // ---------- Filtered & paginated attendance records ----------
    @Query("SELECT a FROM Attendance a JOIN a.employee e LEFT JOIN e.department d WHERE " +
            "(:fromDate IS NULL OR a.date >= :fromDate) AND " +
            "(:toDate IS NULL OR a.date <= :toDate) AND " +
            "(:department IS NULL OR d.departmentName = :department) AND " +
            "(:status IS NULL OR a.status = :status) AND " +
            "(:keyword IS NULL OR " +
            "LOWER(e.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(e.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(e.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "CAST(e.id AS string) LIKE CONCAT('%', :keyword, '%'))")
    Page<Attendance> findFiltered(@Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate,
                                  @Param("department") String department,
                                  @Param("status") AttendanceStatus status,
                                  @Param("keyword") String keyword,
                                  Pageable pageable);

    @Query("SELECT COUNT(a) FROM Attendance a JOIN a.employee e LEFT JOIN e.department d " +
            "WHERE a.date = :date AND (:department IS NULL OR d.departmentName = :department) AND a.status = :status")
    long countByDateAndStatusAndDepartment(@Param("date") LocalDate date,
                                           @Param("status") AttendanceStatus status,
                                           @Param("department") String department);

    @Query("SELECT COUNT(e) FROM Employee e LEFT JOIN e.department d " +
            "WHERE (:department IS NULL OR d.departmentName = :department)")
    long countTotalEmployeesByDepartment(@Param("department") String department);




}