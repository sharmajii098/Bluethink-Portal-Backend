package EmployeeManagementSystem.repository;

import EmployeeManagementSystem.dto.EmployeeTimesheetDTO;
import EmployeeManagementSystem.entity.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {

    /** AI aggregate queries operate inside already verified, server-computed employee scope. */
    @Query("SELECT t.status, COUNT(t) FROM Timesheet t WHERE t.employeeId IN :ids GROUP BY t.status")
    List<Object[]> countStatusesForEmployees(@Param("ids") Collection<String> ids);

    @Query("SELECT t.status, COUNT(t) FROM Timesheet t WHERE t.employeeId IN :ids " +
            "AND t.date BETWEEN :start AND :end GROUP BY t.status")
    List<Object[]> countStatusesForEmployeesBetween(@Param("ids") Collection<String> ids,
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT t.status, COUNT(t) FROM Timesheet t GROUP BY t.status")
    List<Object[]> countAllStatuses();

    List<Timesheet> findTop12ByEmployeeIdInOrderByDateDesc(Collection<String> ids);
    List<Timesheet> findTop12ByEmployeeIdInAndStatusOrderByDateDesc(Collection<String> ids, String status);
    List<Timesheet> findTop12ByEmployeeIdInAndDateBetweenOrderByDateDesc(
            Collection<String> ids, LocalDate start, LocalDate end);
    List<Timesheet> findTop12ByEmployeeIdInAndDateBetweenAndStatusOrderByDateDesc(
            Collection<String> ids, LocalDate start, LocalDate end, String status);
    List<Timesheet> findTop30ByEmployeeIdOrderByDateDesc(String employeeId);


    /**
     * Used by Attendance Calendar
     */
    List<Timesheet> findByEmployeeIdAndDateBetween(
            String employeeId,
            LocalDate startDate,
            LocalDate endDate
    );

    /**
     * Employee Timesheet History
     */
    List<Timesheet> findByEmployeeId(String employeeId);

    /**
     * Employee Timesheet History (Latest First)
     */
    List<Timesheet> findByEmployeeIdOrderByDateDesc(String employeeId);

    java.util.Optional<Timesheet> findByStandupUpdateId(Long standupUpdateId);

    /**
     * Check Timesheet for Particular Day
     */
    List<Timesheet> findByEmployeeIdAndDate(
            String employeeId,
            LocalDate date
    );

    /**
     * Filter by Status
     */
    List<Timesheet> findByEmployeeIdAndStatus(
            String employeeId,
            String status
    );

    /**
     * Check if Timesheet Exists for a Day
     */
    boolean existsByEmployeeIdAndDate(
            String employeeId,
            LocalDate date
    );

    /**
     * Check Approved Timesheet Exists
     */
    boolean existsByEmployeeIdAndDateAndStatus(
            String employeeId,
            LocalDate date,
            String status
    );

    /**
     * Display One Employee Only (No Duplicate Employee IDs)
     */
    @Query("""
    SELECT new EmployeeManagementSystem.dto.EmployeeTimesheetDTO(
        t.employeeId,
        t.employeeName,
        COUNT(t)
    )
    FROM Timesheet t
    GROUP BY t.employeeId, t.employeeName
    ORDER BY t.employeeName
""")
    List<EmployeeTimesheetDTO> getAllEmployees();

    Optional<Timesheet> findById(Long id);
}