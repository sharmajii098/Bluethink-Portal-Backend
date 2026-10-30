package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class HrLetterWorkflow {
 @org.springframework.beans.factory.annotation.Autowired private ReleaseAudit audit;
 @org.springframework.beans.factory.annotation.Autowired private RequestScopeService requestScope;
 private void authorizePreparation(EmployeeLetterRequest letter){
  if("OFFER_LETTER".equals(letter.getLetterType()) && actor.id().equals(letter.getEmployeeUserId())) return;
  requestScope.assertCanReview(actor.has("ADMIN")?"ADMIN":"HR",actor.id(),letter);
 }

 private final EmployeeLetterRequestRepository letters;private final HrLetterTemplateRepository templates;private final EmployeeProfileRepository profiles;
 private final EmployeeSeparationRepository separations;private final SalaryStructureService salaries;private final PortalActor actor;private final LetterPdfService pdf;private final ZoneId zone;
 private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 public HrLetterWorkflow(EmployeeLetterRequestRepository letters,HrLetterTemplateRepository templates,EmployeeProfileRepository profiles,EmployeeSeparationRepository separations,
       SalaryStructureService salaries,PortalActor actor,LetterPdfService pdf,@Value("${bluethink.business-zone:Asia/Kolkata}")String zone){this.letters=letters;this.templates=templates;this.profiles=profiles;this.separations=separations;this.salaries=salaries;this.actor=actor;this.pdf=pdf;this.zone=ZoneId.of(zone);}
 public record Prepare(Long templateId,List<LetterPdfService.Component> compensation){}
 private record Built(LetterPdfService.Snapshot snapshot,String fingerprint){}
 private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Cannot encode letter snapshot",e);}}
 private <T>T read(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception e){throw new IllegalArgumentException("Letter snapshot is unavailable or invalid",e);}}
 private String required(String s,String label){if(s==null||s.isBlank()||s.length()>1500||s.chars().anyMatch(c->c<32||c==127))throw new IllegalArgumentException("Verified "+label+" is required");return s.trim();}
 private String date(LocalDate date,String label){if(date==null)throw new IllegalArgumentException("Verified "+label+" is required");return date.toString();}
 @Transactional public EmployeeLetterRequest prepare(Long id,Prepare input){
  actor.hr();EmployeeLetterRequest x=letters.lock(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));authorizePreparation(x);
  if(Set.of("ISSUED","REJECTED").contains(x.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Finalized letters are immutable");
  Built built=build(x,input);pdf.render(built.snapshot(),true); // Fail before approval if data or glyphs cannot be rendered.
  x.setTemplateId(input.templateId());x.setPreparationInput(write(input));x.setPreparedSnapshot(write(built.snapshot()));x.setLetterSourceFingerprint(built.fingerprint());x.setPreparedBy(actor.id());x.setPreparedAt(LocalDateTime.now(zone));x.setApprovedSnapshotHash(null);x.setLetterApprovedBy(null);x.setStatus("UNDER_REVIEW");
  audit.record("HR_LETTER","PREPARE",x.getId());return letters.save(x);
 }
 @Transactional(readOnly=true) public byte[] preview(Long id){actor.hr();EmployeeLetterRequest x=letters.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));authorizePreparation(x);return pdf.render(read(x.getPreparedSnapshot(),LetterPdfService.Snapshot.class),true);}
 @Transactional public void approve(EmployeeLetterRequest x,String reviewer){
  actor.hr();if(x.getPreparedSnapshot()==null||x.getPreparedBy()==null)throw new IllegalArgumentException("Prepare and preview verified letter data first");
  if(reviewer.equals(x.getPreparedBy())||reviewer.equals(x.getEmployeeUserId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"A different HR/Admin must approve this letter; self-approval is prohibited");
  Built current=build(x,read(x.getPreparationInput(),Prepare.class));
  if(!current.fingerprint().equals(x.getLetterSourceFingerprint()))throw new IllegalArgumentException("HR data changed after preparation. Prepare a fresh preview");
  x.setApprovedSnapshotHash(IntegrationCrypto.sha256(x.getPreparedSnapshot()));x.setLetterApprovedBy(reviewer);audit.record("HR_LETTER","APPROVE",x.getId());
 }
 @Transactional public void issue(EmployeeLetterRequest x){
  actor.hr();if(!"APPROVED".equals(x.getStatus())||x.getApprovedSnapshotHash()==null||!x.getApprovedSnapshotHash().equals(IntegrationCrypto.sha256(x.getPreparedSnapshot())))throw new IllegalArgumentException("An unchanged, approved letter is required");
  Built current=build(x,read(x.getPreparationInput(),Prepare.class));if(!current.fingerprint().equals(x.getLetterSourceFingerprint()))throw new IllegalArgumentException("Verified data or issue date changed. Prepare and approve again");
  LetterPdfService.Snapshot snapshot=read(x.getPreparedSnapshot(),LetterPdfService.Snapshot.class);byte[] bytes=pdf.render(snapshot,false);
  x.setIssuedPdf(bytes);x.setIssuedPdfHash(LetterPdfService.digest(bytes));x.setIssuedAt(LocalDateTime.now(zone));x.setDocumentReference(snapshot.reference());audit.record("HR_LETTER","ISSUE",x.getId());
 }
 private Built build(EmployeeLetterRequest x,Prepare input){
  if(input==null||input.templateId()==null)throw new IllegalArgumentException("Choose an approved template");
  HrLetterTemplate t=templates.findById(input.templateId()).orElseThrow(()->new IllegalArgumentException("Template not found"));
  if(!"APPROVED".equals(t.status)||!x.getLetterType().equals(t.letterType))throw new IllegalArgumentException("Choose an approved template matching the letter type");
  LetterTemplateRules.validate(t.letterType,t.body);LetterTemplateRules.approvable(t.body);
  Map<String,String> v=new TreeMap<>();v.put("NAME",required(x.getEmployeeName(),"name"));v.put("EMPLOYEE_ID",x.getEmployeeUserId());v.put("COMPANY_NAME",t.companyName);v.put("DOCUMENT_REFERENCE","BT-"+x.getRequestNumber());v.put("ISSUE_DATE",LocalDate.now(zone).toString());v.put("SIGNATORY_NAME",t.signatoryName);v.put("SIGNATORY_DESIGNATION",t.signatoryDesignation);
  List<LetterPdfService.Component> components=new ArrayList<>();String verification="";
  if("OFFER_LETTER".equals(x.getLetterType())){
   v.put("DESIGNATION",required(x.getDesignation(),"designation"));v.put("JOINING_DATE",date(x.getJoiningDate(),"joining date"));v.put("OFFER_EXPIRY",date(x.getOfferExpiresOn(),"offer expiry"));
   if(x.getOfferExpiresOn().isBefore(LocalDate.now(zone))||x.getAnnualCtc()==null||x.getAnnualCtc().signum()<=0||x.getProbationMonths()==null||x.getNoticeDays()==null)throw new IllegalArgumentException("Offer terms are incomplete or expired");
   v.put("ANNUAL_CTC",x.getAnnualCtc().toPlainString());v.put("PROBATION_MONTHS",String.valueOf(x.getProbationMonths()));v.put("NOTICE_DAYS",String.valueOf(x.getNoticeDays()));
   if(input.compensation()==null||input.compensation().isEmpty()||input.compensation().size()>30)throw new IllegalArgumentException("Enter 1-30 approved compensation components");
   BigDecimal ctc=BigDecimal.ZERO;Set<String> names=new HashSet<>();
   for(LetterPdfService.Component c:input.compensation()){
    if(c.label()==null||c.label().isBlank()||c.label().length()>100||!names.add(c.label().trim().toLowerCase(Locale.ROOT))||c.annualAmount()==null||c.annualAmount().signum()<0||c.annualAmount().scale()>2||c.annualAmount().precision()>14||c.category()==null||c.label().chars().anyMatch(k->k<32||k==127)||!Set.of("EARNING","EMPLOYER_CONTRIBUTION","DEDUCTION").contains(c.category()))throw new IllegalArgumentException("Invalid or duplicate compensation component");
    if(!"DEDUCTION".equals(c.category()))ctc=ctc.add(c.annualAmount());components.add(c);
   }
   if(ctc.compareTo(x.getAnnualCtc())!=0)throw new IllegalArgumentException("Annual earnings plus employer contributions must equal the approved annual CTC");
  }else{
   EmployeeProfile p=profiles.findByUserId(x.getEmployeeUserId()).orElseThrow(()->new IllegalArgumentException("Verified employee profile required"));Employee e=p.getEmployee();if(e==null)throw new IllegalArgumentException("Linked employee record required");
   v.put("NAME",required(p.getFullName(),"employee name"));v.put("DESIGNATION",required(p.getDesignation(),"designation"));v.put("JOINING_DATE",date(e.getJoiningDate(),"joining date"));
   verification="EMP:"+e.getId()+":"+p.getId()+":"+p.getUpdatedAt()+":"+p.getStatus();
   if(Set.of("EXPERIENCE_LETTER","RELIEVING_LETTER","NO_DUES_CERTIFICATE").contains(x.getLetterType())){
    EmployeeSeparation s=separations.findByUserId(p.getUserId()).orElseThrow(()->new IllegalArgumentException("Completed employee separation record required"));
    if(!"COMPLETED".equals(s.getStatus())||s.getLastWorkingDate()==null||s.getLastWorkingDate().isAfter(LocalDate.now(zone))||s.getLastWorkingDate().isBefore(e.getJoiningDate()))throw new IllegalArgumentException("Verify completed separation and final working date");
    if(Set.of("RELIEVING_LETTER","NO_DUES_CERTIFICATE").contains(x.getLetterType())&&!(Boolean.TRUE.equals(s.getManagerCleared())&&Boolean.TRUE.equals(s.getHrCleared())&&Boolean.TRUE.equals(s.getFinanceCleared())&&Boolean.TRUE.equals(s.getAssetCleared())&&Boolean.TRUE.equals(s.getAccessCleared())))throw new IllegalArgumentException("All manager, HR, finance, asset and access clearances are required");
    v.put("LAST_WORKING_DATE",s.getLastWorkingDate().toString());v.put("CLEARANCE_DATE",date(s.getCompletedDate(),"clearance completion date"));verification+="|SEP:"+s.getId()+":"+s.getUpdatedAt();
   }else if(!"ACTIVE".equalsIgnoreCase(p.getStatus()))throw new IllegalArgumentException("An active employee record is required for this certificate");
   if("ADDRESS_PROOF".equals(x.getLetterType()))v.put("ADDRESS",required(p.getCurrentAddress(),"current address")+", "+required(p.getCurrentCity(),"city")+", "+required(p.getCurrentState(),"state")+" "+required(p.getCurrentPincode(),"postal code"));
   if("SALARY_CERTIFICATE".equals(x.getLetterType())){
    SalaryStructure st=salaries.getSalaryStructureByEmployeeProfileId(p.getId());if(st.getEffectiveFrom()==null||st.getEffectiveFrom().isAfter(LocalDate.now(zone)))throw new IllegalArgumentException("Current verified salary structure required");
    BigDecimal gross=BigDecimal.ZERO;BigDecimal[] amounts={st.getBasicSalary(),st.getHra(),st.getConveyance(),st.getMedicalAllowance(),st.getSpecialAllowance(),st.getOtherAllowance()};String[] labels={"Basic salary","HRA","Conveyance","Medical allowance","Special allowance","Other allowance"};
    for(int n=0;n<amounts.length;n++){BigDecimal amount=amounts[n]==null?BigDecimal.ZERO:amounts[n];if(amount.signum()<0)throw new IllegalArgumentException("Invalid salary structure");gross=gross.add(amount);components.add(new LetterPdfService.Component(labels[n],amount.multiply(BigDecimal.valueOf(12)),"EARNING"));}
    v.put("MONTHLY_GROSS",gross.toPlainString());verification+="|SAL:"+st.getId()+":"+st.getUpdatedAt();
   }
  }
  v.put("COMPENSATION_TABLE","\n@@COMPENSATION@@\n");String body=LetterTemplateRules.render(t.body,v);
  LetterPdfService.Snapshot snapshot=new LetterPdfService.Snapshot(t.title,t.letterType,v.get("DOCUMENT_REFERENCE"),v.get("ISSUE_DATE"),t.companyName,t.companyAddress,t.verificationEmail,t.cin,t.signatoryName,t.signatoryDesignation,body,List.copyOf(components));
  return new Built(snapshot,IntegrationCrypto.sha256(write(snapshot)+"|"+t.id+"|"+t.revision+"|"+verification));
 }
}
