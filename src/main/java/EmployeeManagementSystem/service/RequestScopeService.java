package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.repository.RegisterEmployeeRepository;
import EmployeeManagementSystem.entity.RegisterEmployee;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Central role-aware ownership checks for employee-originated workflow requests.
 *
 * HR and MANAGER personal requests require ADMIN review. Employee requests
 * may be reviewed by ADMIN, HR, or their direct MANAGER. Self-review is denied.
 * Keeping this logic in one place prevents individual request controllers from
 * accidentally exposing another manager's team.
 */
@Service
public class RequestScopeService {

    private static final List<String> OWNER_GETTERS = List.of(
            "getEmployeeUserId",
            "getSubjectUserId",
            "getReferrerUserId",
            "getReporterUserId",
            "getEmployeeId",
            "getUserId",
            "getRequesterUserId",
            "getDonorUserId",
            "getEmployeeCode",
            "getApplicantUserId",
            "getRequestedBy"
    );

    private final EmployeeRepository employees;

    private final RegisterEmployeeRepository accounts;
    public RequestScopeService(EmployeeRepository employees, RegisterEmployeeRepository accounts) {
        this.employees = employees;
        this.accounts = accounts;
    }
    public String requesterRole(String userId) {
        RegisterEmployee account = userId == null ? null : accounts.findByUserId(userId);
        return account == null || account.getRole() == null ? "" : account.getRole().replaceFirst("^ROLE_", "").toUpperCase(Locale.ROOT);
    }
    public boolean canReview(String role, String reviewer, Object record) {
        String owner = ownerUserId(record);
        if (owner == null || owner.equalsIgnoreCase(reviewer)) return false;
        // A candidate offer is authored by HR on behalf of a candidate, not a personal staff request.
        if (record instanceof EmployeeManagementSystem.entity.EmployeeLetterRequest letter
                && "OFFER_LETTER".equals(letter.getLetterType())) return Set.of("ADMIN", "HR").contains(role);
        String requesterRole = requesterRole(owner);
        boolean direct = "MANAGER".equals(role) && employees.findUserIdsByReportingManager(reviewer).contains(owner);
        return StaffRequestPolicy.canReview(role, requesterRole, direct, false);
    }


    public boolean isManagementRole(String role) {
        return "ADMIN".equals(role) || "HR".equals(role) || "MANAGER".equals(role);
    }

    public <T> List<T> visibleTo(String role, String currentUserId, Collection<T> records) {
        if (!isManagementRole(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Management access required.");
        }
        if (records == null || records.isEmpty()) return List.of();
        return records.stream().filter(record -> canReview(role, currentUserId, record)
                || (Set.of("ADMIN","HR").contains(role) && record instanceof EmployeeManagementSystem.entity.EmployeeLetterRequest letter
                    && "OFFER_LETTER".equals(letter.getLetterType()) && Objects.equals(currentUserId,letter.getEmployeeUserId()))).toList();
    }

    public void assertCanReview(String role, String currentUserId, Object record) {
        if (!canReview(role, currentUserId, record)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "HR and Manager personal requests require Admin review. Self-approval is not allowed; employee requests require HR or the direct Manager.");
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

    public void assertCanManageUser(String role, String currentUserId, String targetUserId) {
        if ("ADMIN".equals(role) || "HR".equals(role)) return;
        if (!"MANAGER".equals(role) || targetUserId == null
                || !employees.findUserIdsByReportingManager(currentUserId).contains(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee is outside your direct team.");
        }
    }

    /** For self-service edits/reads that also permit management overrides. */
    public void assertCanAccessById(String role, String currentUserId, Collection<?> records, Long id) {
        Object record = records.stream().filter(Objects::nonNull)
                .filter(x -> Objects.equals(entityId(x), id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
        if (Objects.equals(currentUserId, ownerUserId(record))) return;
        assertCanReview(role, currentUserId, record);
    }

    public String ownerUserId(Object record) {
        if (record == null) return null;
        for (String getter : OWNER_GETTERS) {
            Object value = invoke(record, getter);
            if (value instanceof Number numeric && "getEmployeeId".equals(getter)) {
                return employees.findById(numeric.longValue()).map(e -> e.getRegisterEmployee() == null ? null : e.getRegisterEmployee().getUserId()).orElse(null);
            }
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) return text;
            }
        }
        Object nested = invoke(record, "getEmployee");
        if (nested instanceof Employee employee) {
            if (employee.getRegisterEmployee() != null) return employee.getRegisterEmployee().getUserId();
            if (employee.getEmployeeProfile() != null) return employee.getEmployeeProfile().getUserId();
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
            Method method;
            try { method = target.getClass().getMethod(methodName); }
            catch (NoSuchMethodException missing) {
                if (!target.getClass().isRecord() || !methodName.startsWith("get")) throw missing;
                String component = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                method = target.getClass().getMethod(component);
            }
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
