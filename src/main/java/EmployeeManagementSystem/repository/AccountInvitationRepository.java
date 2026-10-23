package EmployeeManagementSystem.repository;

import EmployeeManagementSystem.entity.AccountInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountInvitationRepository extends JpaRepository<AccountInvitation, Long> {
    Optional<AccountInvitation> findByTokenHash(String tokenHash);
    List<AccountInvitation> findByAccountIdAndConsumedAtIsNull(Long accountId);
}
