package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.ApprovedPayrollPolicy;
import EmployeeManagementSystem.repository.ApprovedPayrollPolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.DayOfWeek;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Framework-dependent tests: execute with Maven in staging. */
class ApprovedPayrollPolicyServiceTest {
 private final ApprovedPayrollPolicyRepository repo=mock(ApprovedPayrollPolicyRepository.class);
 private final PortalActor actor=mock(PortalActor.class);
 private final ReleaseAudit audit=mock(ReleaseAudit.class);
 private final ApprovedPayrollPolicyService service=new ApprovedPayrollPolicyService(repo,actor,audit);
 private PayrollReconciliationRules.Policy policy(String ref){return new PayrollReconciliationRules.Policy(ref,Set.of(DayOfWeek.MONDAY,DayOfWeek.TUESDAY),480,240,true,1,Map.of("CASUAL","PAID"));}
 private ApprovedPayrollPolicy row(String ref,String month,String author,String status){
  ApprovedPayrollPolicy p=new ApprovedPayrollPolicy();p.id=(long)ref.hashCode() & 0xffffffffL;p.reference=ref;p.effectiveFrom=month;p.createdBy=author;p.status=status;
  // Serialize through the implementation when creating drafts; existing row payload isn't needed until ownership gate.
  p.policyJson="{}";return p;
 }
 @Test void unregisteredPayrollVersionFailsClosed(){when(actor.id()).thenReturn("HR2");when(repo.findByReference("HR-2026-V1")).thenReturn(Optional.empty());assertThrows(ResponseStatusException.class,()->service.requireApproved(policy("HR-2026-V1"),"2026-10"));}
 @Test void creatorCannotApproveOwnDraft(){ApprovedPayrollPolicy r=row("HR-2026-V1","2026-10","HR1","DRAFT");when(repo.lock(r.id)).thenReturn(Optional.of(r));when(actor.id()).thenReturn("HR1");assertThrows(ResponseStatusException.class,()->service.approve(r.id));verify(repo,never()).save(any());}
 @Test void alreadyApprovedRecordCannotBeApprovedAgain(){ApprovedPayrollPolicy r=row("HR-2026-V1","2026-10","HR1","APPROVED");when(repo.lock(r.id)).thenReturn(Optional.of(r));assertThrows(ResponseStatusException.class,()->service.approve(r.id));verify(repo,never()).save(any());}
 @Test void duplicateReferencesRejectedOnCreate(){var p=policy("HR-2026-V1");when(repo.findByReference(p.reference())).thenReturn(Optional.of(new ApprovedPayrollPolicy()));assertThrows(ResponseStatusException.class,()->service.create(new ApprovedPayrollPolicyService.Create("2026-10",null,p)));verify(repo,never()).save(any());}
 @Test void backwardDateRangeIsRejected(){assertThrows(ResponseStatusException.class,()->service.create(new ApprovedPayrollPolicyService.Create("2026-12","2026-10",policy("HR-2026-V1"))));}
}
