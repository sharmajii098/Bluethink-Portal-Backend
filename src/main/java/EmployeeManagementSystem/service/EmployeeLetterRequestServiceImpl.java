package EmployeeManagementSystem.service;
import EmployeeManagementSystem.entity.EmployeeLetterRequest;
import EmployeeManagementSystem.repository.EmployeeLetterRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
@Service @Transactional
public class EmployeeLetterRequestServiceImpl implements EmployeeLetterRequestService {
 private final EmployeeLetterRequestRepository repo;
 private static final Set<String> TYPES=Set.of("OFFER_LETTER","EMPLOYMENT_CERTIFICATE","SALARY_CERTIFICATE","EXPERIENCE_LETTER","RELIEVING_LETTER","ADDRESS_PROOF","NO_DUES_CERTIFICATE");
 private static final Set<String> STATUSES=Set.of("UNDER_REVIEW","APPROVED","REJECTED","ISSUED");
 public EmployeeLetterRequestServiceImpl(EmployeeLetterRequestRepository r){repo=r;}
 public List<EmployeeLetterRequest> all(){return repo.findAllByOrderByCreatedAtDesc();}
 public List<EmployeeLetterRequest> my(String u){return repo.findByEmployeeUserIdOrderByCreatedAtDesc(u);}
 public EmployeeLetterRequest create(EmployeeLetterRequest x,String u,String n){
  if(x.getLetterType()==null||!TYPES.contains(x.getLetterType())) throw new IllegalArgumentException("Invalid letter type.");
  if(!"OFFER_LETTER".equals(x.getLetterType())&&repo.existsByEmployeeUserIdAndLetterTypeAndStatusIn(u,x.getLetterType(),List.of("SUBMITTED","UNDER_REVIEW","APPROVED"))) throw new IllegalArgumentException("An active request for this letter type already exists.");
  x.setId(null); x.setRequestNumber("LTR-"+String.format("%06d",new Random().nextInt(1000000))); x.setEmployeeUserId(u); x.setEmployeeName(n); x.setStatus("SUBMITTED"); return repo.save(x);
 }
 public EmployeeLetterRequest review(Long id,Map<String,Object>b,String reviewerId,String reviewerName){
  EmployeeLetterRequest x=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Request not found."));
  if(Set.of("ISSUED","REJECTED").contains(x.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Finalized letters are immutable.");
  String status=String.valueOf(b.getOrDefault("status","UNDER_REVIEW")); if(!STATUSES.contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid status.");
  if(Set.of("APPROVED","ISSUED").contains(status))throw new ResponseStatusException(HttpStatus.CONFLICT,"Verified workflow approval is required.");
  x.setStatus(status); x.setReviewerUserId(reviewerId); x.setReviewerName(reviewerName); x.setHrComment(String.valueOf(b.getOrDefault("comment",""))); x.setReviewedAt(LocalDateTime.now());
  if(!"APPROVED".equals(status)){x.setApprovedSnapshotHash(null);x.setLetterApprovedBy(null);}
  if("ISSUED".equals(status)){x.setIssuedAt(LocalDateTime.now()); if(x.getDocumentReference()==null||x.getDocumentReference().isBlank()) x.setDocumentReference("HRMS-"+x.getRequestNumber()+"-DOCUMENT");}
  return repo.save(x);
 }
}
