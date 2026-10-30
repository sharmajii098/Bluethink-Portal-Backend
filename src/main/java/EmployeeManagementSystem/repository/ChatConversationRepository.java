package EmployeeManagementSystem.repository;
import EmployeeManagementSystem.entity.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from ChatConversation c where c.id = :id")
    java.util.Optional<ChatConversation> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    List<ChatConversation> findDistinctByMembersContainingOrderByCreatedAtDesc(String username);
}
