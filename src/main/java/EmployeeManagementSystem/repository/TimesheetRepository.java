//package EmployeeManagementSystem.repository;
//
//import EmployeeManagementSystem.entity.Timesheet;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.stereotype.Repository;
//
//import java.time.LocalDate;
//import java.util.List;
//import java.util.Optional;
//
//@Repository
//public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {
//
//    /**
//     * Used by Attendance Calendar
//     */
//    List<Timesheet> findByEmployeeIdAndDateBetween(
//            String employeeId,
//            LocalDate startDate,
//            LocalDate endDate
//    );
//
//    /**
//     * Employee Timesheet History
//     */
//    List<Timesheet> findByEmployeeId(String employeeId);
//
//    /**
//     * Check Timesheet for Particular Day
//     */
//    List<Timesheet> findByEmployeeIdAndDate(
//            String employeeId,
//            LocalDate date
//    );
//
//    /**
//     * Filter by Status
//     */
//    List<Timesheet> findByEmployeeIdAndStatus(
//            String employeeId,
//            String status
//    );
//
//    /**
//     * Check if Timesheet Exists for a Day
//     */
//    boolean existsByEmployeeIdAndDate(
//            String employeeId,
//            LocalDate date
//    );
//
//    /**
//     * Check Approved Timesheet Exists
//     */
//    boolean existsByEmployeeIdAndDateAndStatus(
//            String employeeId,
//            LocalDate date,
//            String status
//    );
//
//    Optional<Timesheet> findById(Long id);
//
//    List<Timesheet> findByEmployeeIdOrderByDateDesc(String employeeId);
//}











package EmployeeManagementSystem.repository;

import EmployeeManagementSystem.dto.EmployeeTimesheetDTO;
import EmployeeManagementSystem.entity.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {

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