package EmployeeManagementSystem.service;

import java.util.Set;
import java.util.regex.Pattern;

/** Strict Microsoft Entra tenant segment validation (no loose UUID parsing). */
public final class CalendarTenantPolicy {
    private static final Pattern GUID = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private CalendarTenantPolicy() {}
    public static boolean valid(String tenant) {
        return tenant != null && (Set.of("common", "organizations", "consumers").contains(tenant)
                || GUID.matcher(tenant).matches());
    }
}
