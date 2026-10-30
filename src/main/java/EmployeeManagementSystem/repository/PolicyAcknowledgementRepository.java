package EmployeeManagementSystem.repository;

import EmployeeManagementSystem.entity.PolicyAcknowledgement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PolicyAcknowledgementRepository extends JpaRepository<PolicyAcknowledgement,Long> {
    Optional<PolicyAcknowledgement> findByPolicyIdAndUserId(Long policyId, String userId);
    long countByPolicyIdAndAcknowledgedTrue(Long policyId);
    void deleteByPolicyId(Long policyId);

    // Fetch all of one user's acknowledgements for a bounded batch of visible policy IDs.
    @Query("SELECT a FROM PolicyAcknowledgement a JOIN FETCH a.policy p " +
           "WHERE a.userId = :userId AND p.id IN :ids")
    List<PolicyAcknowledgement> findUserAcknowledgementsForPolicies(
            @Param("userId") String userId, @Param("ids") Collection<Long> ids);

    // One grouped SQL query per batch replaces a per-policy COUNT query.
    @Query("SELECT a.policy.id, COUNT(a) FROM PolicyAcknowledgement a " +
           "WHERE a.acknowledged = true AND a.policy.id IN :ids GROUP BY a.policy.id")
    List<Object[]> countAcknowledgedForPolicies(@Param("ids") Collection<Long> ids);
}
