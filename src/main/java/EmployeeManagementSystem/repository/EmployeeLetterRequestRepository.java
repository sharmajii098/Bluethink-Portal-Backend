package EmployeeManagementSystem.repository;
import EmployeeManagementSystem.entity.EmployeeLetterRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface EmployeeLetterRequestRepository extends JpaRepository<EmployeeLetterRequest,Long>{
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("SELECT l FROM EmployeeLetterRequest l WHERE l.id = :id")
 Optional<EmployeeLetterRequest> lock(@Param("id") Long id);

 List<EmployeeLetterRequest> findByEmployeeUserIdOrderByCreatedAtDesc(String userId);
 List<EmployeeLetterRequest> findAllByOrderByCreatedAtDesc();
 boolean existsByEmployeeUserIdAndLetterTypeAndStatusIn(String userId,String letterType,List<String> statuses);
}
