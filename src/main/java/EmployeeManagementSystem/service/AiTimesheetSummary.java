package EmployeeManagementSystem.service;

import java.util.List;
import java.util.Locale;

/** Converts DB-side group counts without loading entire timesheet tables into Java. */
public record AiTimesheetSummary(long total, long pending, long approved, long rejected) {
    public static AiTimesheetSummary fromStatusCounts(List<Object[]> rows) {
        long total = 0, pending = 0, approved = 0, rejected = 0;
        if (rows != null) for (Object[] row : rows) {
            if (row == null || row.length < 2 || !(row[1] instanceof Number count)) continue;
            long n = count.longValue();
            if (n < 0) throw new IllegalArgumentException("Negative timesheet count");
            total = Math.addExact(total, n);
            String status = row[0] == null ? "" : row[0].toString().toUpperCase(Locale.ROOT);
            switch (status) {
                case "PENDING" -> pending += n;
                case "APPROVED" -> approved += n;
                case "REJECTED" -> rejected += n;
                default -> { /* Cancellation / legacy states still count towards total. */ }
            }
        }
        return new AiTimesheetSummary(total, pending, approved, rejected);
    }
}
