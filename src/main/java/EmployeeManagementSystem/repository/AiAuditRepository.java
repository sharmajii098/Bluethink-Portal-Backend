package EmployeeManagementSystem.repository;
import EmployeeManagementSystem.entity.AiAuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AiAuditRepository extends JpaRepository<AiAuditEntry,Long> {
    long countByEventTypeStartingWithAndResultAndOccurredAtAfter(String eventPrefix, String result, java.time.Instant since);
}
