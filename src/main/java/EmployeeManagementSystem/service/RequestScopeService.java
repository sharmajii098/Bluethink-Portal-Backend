package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Central role-aware ownership checks for employee-originated workflow requests.
 *
 * ADMIN / HR can review organisation-wide requests. MANAGER is restricted to
 * direct reports. EMPLOYEE never gets management access through this service.
 * Keeping this logic in one place prevents individual request controllers from
 * accidentally exposing another manager's team.
 */
@Service
public class RequestScopeService {

    private static final List<String> OWNER_GETTERS = List.of(
            "getEmployeeUserId",
            "getEmployeeId",
            "getUserId",
            "getRequesterUserId",
            "getDonorUserId",
            "getEmployeeCode",
            "getApplicantUserId",
            "getRequestedBy"
    );

    private final EmployeeRepository employees;

    public RequestScopeService(EmployeeRepository employees) {
        this.employees = employees;
    }

    public boolean isManagementRole(String role) {
        return "ADMIN".equals(role) || "HR".equals(role) || "MANAGER".equals(role);
    }

    public <T> List<T> visibleTo(String role, String currentUserId, Collection<T> records) {
        if (records == null || records.isEmpty()) return List.of();
        if ("ADMIN".equals(role) || "HR".equals(role)) return new ArrayList<>(records);
        if (!"MANAGER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Management access required.");
        }

        Set<String> directReports = new HashSet<>(employees.findUserIdsByReportingManager(currentUserId));
        List<T> result = new ArrayList<>();
        for (T record : records) {
            String owner = ownerUserId(record);
            if (owner != null && directReports.contains(owner)) result.add(record);
        }
        return result;
    }

    public void assertCanReview(String role, String currentUserId, Object record) {
        if ("ADMIN".equals(role) || "HR".equals(role)) return;
        if (!"MANAGER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Management access required.");
        }
        String owner = ownerUserId(record);
        if (owner == null || !employees.findUserIdsByReportingManager(currentUserId).contains(owner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Managers can only review requests submitted by their direct reports.");
        }
    }

    public void assertCanReviewById(String role, String currentUserId, Collection<?> records, Long id) {
        Object record = records == null ? null : records.stream()
                .filter(Objects::nonNull)
                .filter(x -> Objects.equals(entityId(x), id))
                .findFirst().orElse(null);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found.");
        }
        assertCanReview(role, currentUserId, record);
    }

    public String ownerUserId(Object record) {
        if (record == null) return null;
        for (String getter : OWNER_GETTERS) {
            Object value = invoke(record, getter);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) return text;
            }
        }
        return null;
    }

    private Long entityId(Object record) {
        Object value = invoke(record, "getId");
        if (value instanceof Number n) return n.longValue();
        if (value != null) {
            try { return Long.valueOf(String.valueOf(value)); }
            catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
