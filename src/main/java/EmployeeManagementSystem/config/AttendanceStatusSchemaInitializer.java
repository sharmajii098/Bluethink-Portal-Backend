package EmployeeManagementSystem.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keeps the legacy MySQL attendance.status ENUM aligned with AttendanceStatus.
 *
 * Older Bluethink databases were created before HOLIDAY, WEEKEND and UNVERIFIED
 * were added to the Java enum. Hibernate ddl-auto=update does not reliably widen
 * an existing MySQL ENUM, so writing UNVERIFIED can fail with
 * "Data truncated for column 'status'". This startup migration is idempotent and
 * only touches the attendance.status column when the application is connected to
 * MySQL/MariaDB and that column still uses ENUM.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!prod & !bootstrap")
@RequiredArgsConstructor
public class AttendanceStatusSchemaInitializer implements ApplicationRunner {

    private static final String ATTENDANCE_STATUS_ENUM =
            "ENUM('PRESENT','ABSENT','LATE','LEAVE','HALF_DAY','HOLIDAY','WEEKEND','UNVERIFIED')";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String databaseProduct;
            try (var connection = jdbcTemplate.getDataSource().getConnection()) {
                databaseProduct = connection.getMetaData().getDatabaseProductName();
            }

            if (databaseProduct == null ||
                    !(databaseProduct.toLowerCase().contains("mysql") ||
                      databaseProduct.toLowerCase().contains("mariadb"))) {
                return;
            }

            String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            if (schema == null || schema.isBlank()) {
                return;
            }

            List<String> columnTypes = jdbcTemplate.query(
                    "SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'attendance' AND COLUMN_NAME = 'status'",
                    (rs, rowNum) -> rs.getString(1),
                    schema
            );

            if (columnTypes.isEmpty()) {
                return;
            }

            String currentType = columnTypes.get(0);
            if (currentType == null || !currentType.toLowerCase().startsWith("enum(")) {
                return;
            }

            String normalized = currentType.toUpperCase().replace(" ", "");
            boolean alreadyCompatible = normalized.contains("'PRESENT'")
                    && normalized.contains("'ABSENT'")
                    && normalized.contains("'LATE'")
                    && normalized.contains("'LEAVE'")
                    && normalized.contains("'HALF_DAY'")
                    && normalized.contains("'HOLIDAY'")
                    && normalized.contains("'WEEKEND'")
                    && normalized.contains("'UNVERIFIED'");

            if (alreadyCompatible) {
                return;
            }

            jdbcTemplate.execute(
                    "ALTER TABLE attendance MODIFY COLUMN status " + ATTENDANCE_STATUS_ENUM + " NULL"
            );
            log.info("Updated attendance.status ENUM to include all AttendanceStatus values.");
        } catch (Exception ex) {
            // Do not prevent the whole HRMS from starting if the DB account cannot alter schema.
            // The log includes the exact one-time SQL fallback that an administrator can run.
            log.error(
                    "Could not update attendance.status ENUM automatically. Run this once on the active database: " +
                            "ALTER TABLE attendance MODIFY COLUMN status " + ATTENDANCE_STATUS_ENUM + " NULL;",
                    ex
            );
        }
    }
}
