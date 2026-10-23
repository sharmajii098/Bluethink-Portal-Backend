package EmployeeManagementSystem.repository;
import EmployeeManagementSystem.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop100ByConversation_IdOrderByIdDesc(Long conversationId);
    List<ChatMessage> findTop100ByConversation_IdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId);
    Page<ChatMessage> findByConversation_IdAndIdLessThanEqualOrderByIdDesc(
            Long conversationId, Long throughId, Pageable pageable);
    boolean existsByIdAndConversation_Id(Long id, Long conversationId);
    List<ChatMessage> findTop1ByConversation_IdOrderByCreatedAtDesc(Long conversationId);

    @Query("""
            SELECT COUNT(m)
            FROM ChatMessage m
            WHERE m.conversation.id = :conversationId
              AND LOWER(m.sender) <> LOWER(:username)
              AND NOT EXISTS (
                  SELECT r.id
                  FROM ChatReadReceipt r
                  WHERE r.messageId = m.id
                    AND LOWER(r.username) = LOWER(:username)
              )
            """)
    long countUnreadByConversationAndUsername(
            @Param("conversationId") Long conversationId,
            @Param("username") String username);
}
