package EmployeeManagementSystem.service;
import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReleaseAuthorizationTest {
 private void signIn(String user,String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,"test-only",List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void anonymousIsRejected(){assertThrows(ResponseStatusException.class,()->new PortalActor().id());}
 @Test void employeesCannotAdministerMeetings(){signIn("EMP_TEST","EMPLOYEE");assertThrows(ResponseStatusException.class,()->new PortalActor().meetingEditor());}
 @Test void managerMayEditOnlyOwnMeeting(){signIn("MGR_TEST","MANAGER");PortalMeeting m=new PortalMeeting();m.organizer="MGR_OTHER";assertThrows(ResponseStatusException.class,()->new PortalActor().edit(m));m.organizer="MGR_TEST";assertDoesNotThrow(()->new PortalActor().edit(m));}
 @Test void templateAuthorCannotApprove(){signIn("HR_TEST","HR");HrLetterTemplate t=new HrLetterTemplate();t.id=1L;t.createdBy="HR_TEST";HrLetterTemplateRepository repo=mock(HrLetterTemplateRepository.class);when(repo.lock(1L)).thenReturn(Optional.of(t));var service=new HrLetterTemplateService(repo,new PortalActor());assertThrows(ResponseStatusException.class,()->service.approve(1L));verify(repo,never()).save(any());}
 @Test void approvedTemplateCannotBeEdited(){signIn("HR_TEST","HR");HrLetterTemplate t=new HrLetterTemplate();t.id=1L;t.createdBy="HR_TEST";t.status="APPROVED";HrLetterTemplateRepository repo=mock(HrLetterTemplateRepository.class);when(repo.lock(1L)).thenReturn(Optional.of(t));var service=new HrLetterTemplateService(repo,new PortalActor());assertThrows(ResponseStatusException.class,()->service.update(1L,new HrLetterTemplateService.Input("TEST",1,"EMPLOYMENT_CERTIFICATE","Title","Company","Address","hr@example.invalid","","Signer","HR","Body")));verify(repo,never()).save(any());}
 @Test void differentHrApprovesVerifiedTemplateAndAuditIsRecorded(){signIn("HR_SECOND","HR");HrLetterTemplate t=new HrLetterTemplate();t.id=1L;t.createdBy="HR_FIRST";t.letterType="EMPLOYMENT_CERTIFICATE";t.body=HrLetterStarters.all().get(t.letterType);HrLetterTemplateRepository repo=mock(HrLetterTemplateRepository.class);when(repo.lock(1L)).thenReturn(Optional.of(t));when(repo.save(t)).thenReturn(t);ReleaseAudit audit=mock(ReleaseAudit.class);var service=new HrLetterTemplateService(repo,new PortalActor());ReflectionTestUtils.setField(service,"audit",audit);assertEquals("APPROVED",service.approve(1L).status);assertEquals("HR_SECOND",t.approvedBy);verify(audit).record("HR_TEMPLATE","APPROVE",1L);}
 @Test void helperAssertionsAlsoRunInMaven(){ReleaseRulesSelfTest.main(new String[0]);}
}
