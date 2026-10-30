package EmployeeManagementSystem.repository;
import EmployeeManagementSystem.entity.AiChatEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface AiChatEntryRepository extends JpaRepository<AiChatEntry,Long> {
    List<AiChatEntry> findByOwnerIdOrderByCreatedAtDesc(String ownerId, Pageable page);
    Optional<AiChatEntry> findByIdAndOwnerId(Long id,String ownerId);
    long deleteByOwnerId(String ownerId);
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("delete from AiChatEntry e where e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant before);
    Optional<AiChatEntry> findFirstByOwnerIdOrderByCreatedAtDesc(String ownerId);
}
