package EmployeeManagementSystem.repository;

import EmployeeManagementSystem.entity.UserSession;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository
        extends JpaRepository<UserSession, Long> {

    // ============================================================
    // FIND SESSION BY JWT TOKEN
    // ============================================================

    Optional<UserSession> findByJwtToken(String jwtToken);


    // ============================================================
    // FIND ACTIVE SESSION BY JWT TOKEN
    // ============================================================

    Optional<UserSession> findByJwtTokenAndIsActiveTrue(
            String jwtToken
    );


    // ============================================================
    // FIND ALL SESSIONS FOR EMPLOYEE
    // ============================================================

    List<UserSession> findByEmployeeIdOrderByCreatedAtDesc(
            String employeeId
    );


    // ============================================================
    // FIND ACTIVE SESSIONS FOR EMPLOYEE
    // ============================================================

    List<UserSession> findByEmployeeIdAndIsActiveTrueOrderByCreatedAtDesc(
            String employeeId
    );


    // ============================================================
    // FIND ACTIVE SESSIONS FOR EMPLOYEE
    // ============================================================

    List<UserSession> findByEmployeeIdAndIsActiveTrue(
            String employeeId
    );


    // ============================================================
    // FIND SESSIONS FOR MULTIPLE EMPLOYEES
    //
    // Used by HR / MANAGER session monitoring.
    // ============================================================

    List<UserSession> findByEmployeeIdInOrderByCreatedAtDesc(
            List<String> employeeIds
    );


    // ============================================================
    // FIND ALL SESSIONS
    //
    // Used by ADMIN session monitoring.
    // ============================================================

    List<UserSession> findAllByOrderByCreatedAtDesc();


    // ============================================================
    // DEACTIVATE OTHER SESSIONS FOR EMPLOYEE
    // ============================================================

    @Modifying
    @Transactional
    @Query("""
            UPDATE UserSession u
            SET u.isActive = false,
                u.logoutAt = CURRENT_TIMESTAMP
            WHERE u.employeeId = :employeeId
              AND u.isActive = true
              AND u.jwtToken <> :jwtToken
            """)
    int deactivateOtherEmployeeSessions(
            @Param("employeeId") String employeeId,
            @Param("jwtToken") String jwtToken
    );


    // ============================================================
    // DEACTIVATE ALL ACTIVE SESSIONS FOR EMPLOYEE
    // ============================================================

    @Modifying
    @Transactional
    @Query("""
            UPDATE UserSession u
            SET u.isActive = false,
                u.logoutAt = CURRENT_TIMESTAMP
            WHERE u.employeeId = :employeeId
              AND u.isActive = true
            """)
    void deactivateEmployeeSessions(
            @Param("employeeId") String employeeId
    );
}