package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.Policy;
import EmployeeManagementSystem.entity.PolicyAcknowledgement;
import EmployeeManagementSystem.repository.PolicyAcknowledgementRepository;
import EmployeeManagementSystem.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PolicyServiceImpl implements PolicyService {

    private final PolicyRepository policyRepository;
    private final PolicyAcknowledgementRepository acknowledgementRepository;

    @Override
    public List<Map<String, Object>> listForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("Verified user ID is required");
        }
        LocalDate today = LocalDate.now();
        List<Policy> effectivePolicies = policyRepository.findAllByOrderByEffectiveDateDesc().stream()
                .filter(p -> PolicyVisibility.isEffective(p.getEffectiveDate(), today))
                .toList();
        if (effectivePolicies.isEmpty()) return List.of();

        // Batch lookups avoid two queries for every policy (N+1) and cap IN-list size.
        Map<Long, PolicyAcknowledgement> own = new java.util.HashMap<>();
        Map<Long, Long> counts = new java.util.HashMap<>();
        List<Long> ids = effectivePolicies.stream().map(Policy::getId).toList();
        for (int offset = 0; offset < ids.size(); offset += 300) {
            List<Long> batch = ids.subList(offset, Math.min(offset + 300, ids.size()));
            for (PolicyAcknowledgement acknowledgement :
                    acknowledgementRepository.findUserAcknowledgementsForPolicies(userId, batch)) {
                own.put(acknowledgement.getPolicy().getId(), acknowledgement);
            }
            for (Object[] count : acknowledgementRepository.countAcknowledgedForPolicies(batch)) {
                counts.put(((Number) count[0]).longValue(), ((Number) count[1]).longValue());
            }
        }

        List<Map<String, Object>> out = new ArrayList<>(effectivePolicies.size());
        for (Policy p : effectivePolicies) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("title", p.getTitle());
            row.put("description", p.getDescription());
            row.put("category", p.getCategory());
            row.put("documentPath", p.getDocumentPath());
            row.put("effectiveDate", p.getEffectiveDate());
            PolicyAcknowledgement acknowledgement = own.get(p.getId());
            row.put("acknowledged", acknowledgement != null && acknowledgement.isAcknowledged());
            row.put("acknowledgedAt", acknowledgement == null ? null : acknowledgement.getAcknowledgedAt());
            row.put("acknowledgementCount", counts.getOrDefault(p.getId(), 0L));
            out.add(row);
        }
        return out;
    }

    @Override
    public List<Policy> listAll() {
        return policyRepository.findAllByOrderByEffectiveDateDesc();
    }

    @Override
    public Policy get(Long id) {

        return policyRepository
                .findById(id)
                .orElseThrow(
                        () -> new NoSuchElementException("Policy not found")
                );
    }

    @Override
    public Policy getVisible(Long id) {
        Policy policy = get(id);
        if (!PolicyVisibility.isEffective(policy.getEffectiveDate(), LocalDate.now())) {
            // Do not reveal the existence or contents of future-effective policies.
            throw new NoSuchElementException("Policy not found");
        }
        return policy;
    }

    /** Acknowledgements are bound to the exact policy wording employees saw.
     * A substantive revision must require acknowledgement again. Do both writes
     * in one transaction so a failure cannot orphan previous acknowledgements.
     */
    @Override
    @Transactional
    public Policy save(Policy p) {
        if (p == null) throw new IllegalArgumentException("Policy is required");
        if (p.getTitle() == null || p.getTitle().isBlank())
            throw new IllegalArgumentException("Policy title is required");
        if (p.getEffectiveDate() == null) p.setEffectiveDate(LocalDate.now());

        boolean contentChanged = false;
        if (p.getId() != null) {
            Policy previous = policyRepository.findById(p.getId())
                    .orElseThrow(() -> new NoSuchElementException("Policy not found"));
            contentChanged = !Objects.equals(previous.getTitle(), p.getTitle())
                    || !Objects.equals(previous.getDescription(), p.getDescription())
                    || !Objects.equals(previous.getCategory(), p.getCategory())
                    || !Objects.equals(previous.getDocumentPath(), p.getDocumentPath())
                    || !Objects.equals(previous.getEffectiveDate(), p.getEffectiveDate());
        }
        Policy saved = policyRepository.save(p);
        if (contentChanged) {
            acknowledgementRepository.deleteByPolicyId(saved.getId());
        }
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long id) {

        if (!policyRepository.existsById(id)) {
            throw new NoSuchElementException("Policy not found");
        }

        acknowledgementRepository.deleteByPolicyId(id);

        policyRepository.deleteById(id);
    }

    @Override
    @Transactional
    public Map<String, Object> acknowledge(Long id, String userId) {

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        Policy policy = getVisible(id);

        PolicyAcknowledgement acknowledgement =
                acknowledgementRepository
                        .findByPolicyIdAndUserId(id, userId)
                        .orElseGet(PolicyAcknowledgement::new);

        acknowledgement.setPolicy(policy);
        acknowledgement.setUserId(userId);
        acknowledgement.setAcknowledged(true);
        acknowledgement.setAcknowledgedAt(LocalDateTime.now());

        acknowledgementRepository.save(acknowledgement);

        return Map.of(
                "success", true,
                "policyId", id,
                "acknowledged", true,
                "acknowledgedAt", acknowledgement.getAcknowledgedAt()
        );
    }
}