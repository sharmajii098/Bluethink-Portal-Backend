package EmployeeManagementSystem.repository;
import EmployeeManagementSystem.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @org.springframework.data.jpa.repository.Query("select count(m) from ChatMessage m where m.conversation.id = :id and m.sender <> :username and not exists (select r.id from ChatReadReceipt r where r.messageId=m.id and r.username=:username)")
    long unread(@org.springframework.data.repository.query.Param("id") Long id,@org.springframework.data.repository.query.Param("username") String username);
    List<ChatMessage> findTop300ByConversation_IdOrderByCreatedAtDescIdDesc(Long conversationId);
    List<ChatMessage> findTop1ByConversation_IdOrderByCreatedAtDesc(Long conversationId);
    List<ChatMessage> findByConversation_IdAndIdLessThanOrderByIdDesc(Long conversationId,Long before,org.springframework.data.domain.Pageable page);
}
