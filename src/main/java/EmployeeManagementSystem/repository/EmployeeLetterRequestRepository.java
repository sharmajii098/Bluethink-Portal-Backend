package EmployeeManagementSystem.repository;
import EmployeeManagementSystem.entity.EmployeeLetterRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EmployeeLetterRequestRepository extends JpaRepository<EmployeeLetterRequest,Long>{
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select l from EmployeeLetterRequest l where l.id=:id")
 Optional<EmployeeLetterRequest> lock(@org.springframework.data.repository.query.Param("id") Long id);
 List<EmployeeLetterRequest> findByEmployeeUserIdOrderByCreatedAtDesc(String userId);
 List<EmployeeLetterRequest> findAllByOrderByCreatedAtDesc();
 boolean existsByEmployeeUserIdAndLetterTypeAndStatusIn(String userId,String letterType,List<String> statuses);
}
