package EmployeeManagementSystem.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** ISO calendar weeks: Monday through Sunday, never overlapping. */
public final class AiCalendarWeeks {
    private AiCalendarWeeks() {}
    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    public static LocalDate weekEnd(LocalDate date) { return weekStart(date).plusDays(6); }
}
