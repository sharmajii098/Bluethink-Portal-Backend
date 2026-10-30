package EmployeeManagementSystem.repository;
import EmployeeManagementSystem.entity.AiKnowledgeChunk;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
public interface AiKnowledgeRepository extends JpaRepository<AiKnowledgeChunk,String> {
 @Query("select k from AiKnowledgeChunk k where k.published=true and k.allowedRoles like :role and (k.departmentId is null or k.departmentId=:department) and k.embeddingModel=:model order by k.createdAt desc,k.sourceId,k.chunkIndex")
 List<AiKnowledgeChunk> authorized(@Param("role")String role,@Param("department")Long department,@Param("model")String model,Pageable page);
 @Query("select k from AiKnowledgeChunk k where k.id in :ids and k.published=true and k.allowedRoles like :role and (k.departmentId is null or k.departmentId=:department) and k.embeddingModel=:model")
 List<AiKnowledgeChunk> authorizedByIds(@Param("ids")List<String> ids,@Param("role")String role,@Param("department")Long department,@Param("model")String model);
 List<AiKnowledgeChunk> findAllByOrderByCreatedAtDesc(Pageable page);
 @Modifying @Transactional @Query("update AiKnowledgeChunk k set k.published=false where k.sourceId=:source") int unpublish(@Param("source")String source);
}
