package EmployeeManagementSystem.service;
import EmployeeManagementSystem.repository.*;
import EmployeeManagementSystem.entity.RegisterEmployee;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class V26KnowledgeAuthorizationTest {
 private final AiKnowledgeRepository repo=mock(AiKnowledgeRepository.class);
 private final AiProviderClient provider=mock(AiProviderClient.class);
 private final PortalActor actor=mock(PortalActor.class);
 private final RegisterEmployeeRepository accounts=mock(RegisterEmployeeRepository.class);
 private final DepartmentRepository departments=mock(DepartmentRepository.class);
 private final UploadSafety scanner=mock(UploadSafety.class);
 private AiKnowledgeService service(){return new AiKnowledgeService(departments,repo,provider,actor,accounts,scanner,mock(AiModelConcurrencyGate.class),mock(AiModelCircuitBreaker.class));}
 @Test void nonAdminCannotListPublishOrUnpublishSources() throws Exception {
  var service=service();assertThrows(ResponseStatusException.class,service::sources);assertThrows(ResponseStatusException.class,()->service.unpublish("source"));assertThrows(ResponseStatusException.class,()->service.publish(null,"Policy","EMPLOYEE",null,true));verifyNoInteractions(repo,provider,scanner,departments);
 }
 @Test void publishingRequiresApprovalBeforeReadingFileOrCallingEmbeddings() throws Exception {
  when(actor.has("ADMIN")).thenReturn(true);when(actor.id()).thenReturn("ADMIN01");var liveAdmin=new RegisterEmployee();liveAdmin.setRole("ADMIN");when(accounts.findByUserId("ADMIN01")).thenReturn(liveAdmin);when(provider.knowledgeEnabled()).thenReturn(true);var service=service();assertThrows(ResponseStatusException.class,()->service.publish(null,"Policy","EMPLOYEE",null,false));verifyNoInteractions(repo,scanner,departments);verify(provider,never()).embed(anyList(),anyBoolean());
 }
 @Test void revokedAdminRoleCannotManageKnowledgeUsingOldJwt(){
  when(actor.has("ADMIN")).thenReturn(true);
  when(actor.id()).thenReturn("FORMERADMIN");
  var account=new RegisterEmployee();account.setRole("EMPLOYEE");
  when(accounts.findByUserId("FORMERADMIN")).thenReturn(account);
  var service=service();
  assertThrows(ResponseStatusException.class,service::sources);
  assertThrows(ResponseStatusException.class,()->service.unpublish("source"));
  assertThrows(ResponseStatusException.class,()->service.reindex(true));
  verifyNoInteractions(repo,provider,scanner,departments);
 }
 @Test void staleRoleIsRejectedBeforeRetrievalOrExternalProcessing(){
  when(provider.knowledgeEnabled()).thenReturn(true);when(actor.id()).thenReturn("EMP01");var account=new RegisterEmployee();account.setRole("HR");when(accounts.findByUserId("EMP01")).thenReturn(account);var service=service();assertThrows(ResponseStatusException.class,()->service.answer("Search company knowledge: policy"));verifyNoInteractions(repo);verify(provider,never()).embed(anyList(),anyBoolean());
 }
}
