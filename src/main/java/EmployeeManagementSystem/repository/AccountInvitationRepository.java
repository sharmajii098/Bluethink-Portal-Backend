package EmployeeManagementSystem.repository;

import EmployeeManagementSystem.entity.AccountInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountInvitationRepository extends JpaRepository<AccountInvitation, Long> {
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update AccountInvitation t set t.consumedAt = :now where t.id = :id and t.consumedAt is null and t.expiresAt > :now")
    int consumeIfValid(@org.springframework.data.repository.query.Param("id") Long id,
                       @org.springframework.data.repository.query.Param("now") java.time.Instant now);
    Optional<AccountInvitation> findByTokenHash(String tokenHash);
    List<AccountInvitation> findByAccountIdAndConsumedAtIsNull(Long accountId);
}
