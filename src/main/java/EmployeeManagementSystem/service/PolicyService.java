package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.Policy;

import java.util.List;
import java.util.Map;

public interface PolicyService {

    List<Map<String, Object>> listForUser(String userId);

    List<Policy> listAll();

    Policy get(Long id);

    /** Effective-only read for ordinary authenticated users. */
    Policy getVisible(Long id);

    Policy save(Policy policy);

    void delete(Long id);

    Map<String, Object> acknowledge(Long id, String userId);
}