package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.entity.EmployeeLetterRequest;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.service.EmployeeLetterRequestService; import EmployeeManagementSystem.service.RequestScopeService;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import EmployeeManagementSystem.repository.EmployeeLetterRequestRepository;
@RestController @RequestMapping("/api/letter-requests")
public class EmployeeLetterRequestController{
 private final EmployeeManagementSystem.service.HrLetterWorkflow workflow; private final EmployeeLetterRequestService service; private final EmployeeManagementSystem.service.LetterPdfService pdf; private final JwtUtil jwt; private final EmployeeRepository employees; private final RequestScopeService scope; private final EmployeeLetterRequestRepository repo;
 public EmployeeLetterRequestController(EmployeeLetterRequestService s,JwtUtil j,EmployeeRepository e,RequestScopeService requestScope,EmployeeManagementSystem.service.LetterPdfService letterPdf,EmployeeLetterRequestRepository letterRepository,EmployeeManagementSystem.service.HrLetterWorkflow workflow){this.workflow=workflow;repo=letterRepository;pdf=letterPdf;service=s;jwt=j;employees=e;scope=requestScope;}
 private String token(String a){if(a==null||!a.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required.");return a.substring(7);}
 private String user(String a){return jwt.extractUsername(token(a));} private String role(String a){String r=jwt.extractRole(token(a));return r==null?"":r.replace("ROLE_","").toUpperCase();} private boolean management(String r){return Set.of("ADMIN","HR","MANAGER").contains(r);}
 @GetMapping public List<EmployeeLetterRequest> all(@RequestHeader("Authorization") String a){String currentRole=role(a);if(!management(currentRole))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");return scope.visibleTo(currentRole,user(a),service.all());}
 @GetMapping("/my") public List<EmployeeLetterRequest> my(@RequestHeader("Authorization") String a){return service.my(user(a));}
 @PostMapping public EmployeeLetterRequest create(@RequestHeader("Authorization") String a,@RequestBody EmployeeLetterRequest x){String u=user(a);String n=employees.findByRegisterEmployee_UserId(u).map(e->e.getFullName()).orElse(u);try{return service.create(x,u,n);}catch(ResponseStatusException e){throw e;}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
 /** HR creates a candidate offer; a second explicit approval step remains mandatory. */
 public record OfferInput(String candidateName,String designation,LocalDate joiningDate,BigDecimal annualCtc,Integer probationMonths,Integer noticeDays,LocalDate offerExpiresOn){}
 @PostMapping("/offers")
 public EmployeeLetterRequest createOffer(@RequestHeader("Authorization") String authorization,@RequestBody OfferInput input){
  String r=role(authorization); if(!Set.of("ADMIN","HR").contains(r))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"HR authorization required");
  if(input==null || input.candidateName()==null || input.candidateName().trim().isEmpty() || input.candidateName().length()>120 || input.designation()==null || input.designation().trim().isEmpty() || input.designation().length()>120 || input.joiningDate()==null || input.annualCtc()==null || input.annualCtc().signum()<=0 || input.annualCtc().compareTo(new BigDecimal("1000000000"))>0 || input.annualCtc().scale()>2 || input.probationMonths()==null || input.probationMonths()<0 || input.probationMonths()>24 || input.noticeDays()==null || input.noticeDays()<0 || input.noticeDays()>365 || input.offerExpiresOn()==null || input.offerExpiresOn().isBefore(LocalDate.now()) || input.joiningDate().isBefore(LocalDate.now().minusDays(30)) || input.offerExpiresOn().isAfter(input.joiningDate().plusDays(30))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid offer fields");
  EmployeeLetterRequest x=new EmployeeLetterRequest(); x.setRequestNumber("OFR-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT));
  x.setLetterType("OFFER_LETTER"); x.setEmployeeUserId(user(authorization)); x.setEmployeeName(input.candidateName().trim());x.setDesignation(input.designation().trim());x.setJoiningDate(input.joiningDate());x.setAnnualCtc(input.annualCtc());x.setProbationMonths(input.probationMonths());x.setNoticeDays(input.noticeDays());x.setOfferExpiresOn(input.offerExpiresOn());x.setStatus("SUBMITTED");
  return repo.save(x);
 }
 @PatchMapping("/{id}/review") public EmployeeLetterRequest review(@RequestHeader("Authorization") String a,@PathVariable Long id,@RequestBody Map<String,Object>b){String currentRole=role(a);if(!management(currentRole))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");scope.assertCanReviewById(currentRole,user(a),service.all(),id);if("MANAGER".equals(currentRole) && service.all().stream().anyMatch(x->Objects.equals(x.getId(),id)&&"OFFER_LETTER".equals(x.getLetterType())))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Offer approval requires HR or Admin");String u=user(a);String n=employees.findByRegisterEmployee_UserId(u).map(e->e.getFullName()).orElse(u);try{return service.review(id,b,u,n);}catch(ResponseStatusException e){throw e;}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
 @PostMapping("/{id}/prepare") public EmployeeLetterRequest prepare(@PathVariable Long id,@RequestBody EmployeeManagementSystem.service.HrLetterWorkflow.Prepare input){return workflow.prepare(id,input);}
 @GetMapping(value="/{id}/preview",produces=MediaType.APPLICATION_PDF_VALUE) public ResponseEntity<byte[]> preview(@PathVariable Long id){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff").body(workflow.preview(id));}
 @GetMapping(value="/{id}/pdf",produces=MediaType.APPLICATION_PDF_VALUE)
 public ResponseEntity<byte[]> download(@RequestHeader("Authorization") String a,@PathVariable Long id){
  String currentUser=user(a); String currentRole=role(a);
  EmployeeLetterRequest letter=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if("MANAGER".equals(currentRole)&&!currentUser.equals(letter.getEmployeeUserId())&&Set.of("OFFER_LETTER","SALARY_CERTIFICATE").contains(letter.getLetterType()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Confidential letter requires HR permission");
  if(!currentUser.equals(letter.getEmployeeUserId())){
   if(!management(currentRole)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
   scope.assertCanReviewById(currentRole,currentUser,service.all(),id);
  }
  if(!"ISSUED".equals(letter.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Letter is not issued");
  byte[] bytes=pdf.generate(letter);
  return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=bluethink-letter-"+id+".pdf")
   .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff").body(bytes);
 }

}
