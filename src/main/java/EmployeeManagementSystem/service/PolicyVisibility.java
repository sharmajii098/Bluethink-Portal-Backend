package EmployeeManagementSystem.service;

import java.time.LocalDate;

/** Single effective-date rule for list, detail and acknowledgement paths. */
public final class PolicyVisibility {
    private PolicyVisibility() {}

    public static boolean isEffective(LocalDate effectiveDate, LocalDate today) {
        if (today == null) throw new IllegalArgumentException("Current date is required");
        return effectiveDate == null || !effectiveDate.isAfter(today);
    }
}
