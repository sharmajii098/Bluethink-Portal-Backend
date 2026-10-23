package EmployeeManagementSystem.repository;
import EmployeeManagementSystem.entity.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    List<ChatConversation> findDistinctByMembersContainingOrderByCreatedAtDesc(String username);
}
