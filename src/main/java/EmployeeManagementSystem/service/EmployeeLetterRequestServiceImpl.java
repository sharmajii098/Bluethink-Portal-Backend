package EmployeeManagementSystem.service;
import EmployeeManagementSystem.entity.EmployeeLetterRequest;
import EmployeeManagementSystem.repository.EmployeeLetterRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
@Service @Transactional
public class EmployeeLetterRequestServiceImpl implements EmployeeLetterRequestService {
 private final EmployeeLetterRequestRepository repo;
 private final HrLetterWorkflow workflow;
 private final PortalActor actor;
 private static final Set<String> TYPES=Set.of("EMPLOYMENT_CERTIFICATE","SALARY_CERTIFICATE","EXPERIENCE_LETTER","RELIEVING_LETTER","ADDRESS_PROOF","NO_DUES_CERTIFICATE","OFFER_LETTER");
 private static final Set<String> STATUSES=Set.of("UNDER_REVIEW","APPROVED","REJECTED","ISSUED");
 public EmployeeLetterRequestServiceImpl(EmployeeLetterRequestRepository r,HrLetterWorkflow workflow,PortalActor actor){repo=r;this.workflow=workflow;this.actor=actor;}
 public List<EmployeeLetterRequest> all(){return repo.findAllByOrderByCreatedAtDesc();}
 public List<EmployeeLetterRequest> my(String u){return repo.findByEmployeeUserIdOrderByCreatedAtDesc(u);}
 public EmployeeLetterRequest create(EmployeeLetterRequest x,String u,String n){
  if(x.getLetterType()==null||!TYPES.contains(x.getLetterType())||"OFFER_LETTER".equals(x.getLetterType())) throw new IllegalArgumentException("Invalid letter type.");
  if(repo.existsByEmployeeUserIdAndLetterTypeAndStatusIn(u,x.getLetterType(),List.of("SUBMITTED","UNDER_REVIEW","APPROVED"))) throw new IllegalArgumentException("An active request for this letter type already exists.");
  x.setVersion(null);x.setTemplateId(null);x.setPreparationInput(null);x.setPreparedSnapshot(null);x.setApprovedSnapshotHash(null);x.setLetterSourceFingerprint(null);x.setPreparedBy(null);x.setPreparedAt(null);x.setLetterApprovedBy(null);x.setIssuedPdf(null);x.setIssuedPdfHash(null);
  if(x.getPurpose()!=null&&x.getPurpose().length()>255||x.getEmployeeComment()!=null&&x.getEmployeeComment().length()>2000||x.getRequestedFor()!=null&&x.getRequestedFor().length()>255)throw new IllegalArgumentException("Letter request text is too long");
  x.setCreatedAt(null);x.setUpdatedAt(null);
  x.setDesignation(null); x.setJoiningDate(null); x.setAnnualCtc(null); x.setProbationMonths(null); x.setNoticeDays(null); x.setOfferExpiresOn(null);
  x.setReviewerUserId(null); x.setReviewerName(null); x.setReviewedAt(null); x.setIssuedAt(null); x.setDocumentReference(null); x.setHrComment(null);
  x.setId(null); x.setRequestNumber("LTR-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT)); x.setEmployeeUserId(u); x.setEmployeeName(n); x.setStatus("SUBMITTED"); return repo.save(x);
 }
 public EmployeeLetterRequest review(Long id,Map<String,Object>b,String reviewerId,String reviewerName){
  EmployeeLetterRequest x=repo.lock(id).orElseThrow(()->new IllegalArgumentException("Request not found."));
  String status=String.valueOf(b.getOrDefault("status","UNDER_REVIEW")); if(!STATUSES.contains(status)) throw new IllegalArgumentException("Invalid status.");
  if("ISSUED".equals(x.getStatus()) || "REJECTED".equals(x.getStatus())) throw new IllegalArgumentException("Finalized letters cannot be modified.");
  if("ISSUED".equals(status) && !"APPROVED".equals(x.getStatus())) throw new IllegalArgumentException("Letter must be approved before issuing.");
  if(Set.of("APPROVED","ISSUED").contains(status))actor.hr();
  if("APPROVED".equals(status))workflow.approve(x,reviewerId);
  if("ISSUED".equals(status))workflow.issue(x);
  if("UNDER_REVIEW".equals(status)){x.setApprovedSnapshotHash(null);x.setLetterApprovedBy(null);}
  String comment=String.valueOf(b.getOrDefault("comment",""));if(comment.length()>1500)throw new IllegalArgumentException("Comment is too long");
  x.setStatus(status); x.setReviewerUserId(reviewerId); x.setReviewerName(reviewerName); x.setHrComment(comment); x.setReviewedAt(LocalDateTime.now());
  return repo.save(x);
 }
}
