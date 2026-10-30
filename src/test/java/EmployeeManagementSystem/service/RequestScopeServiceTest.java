package EmployeeManagementSystem.service;
import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.repository.RegisterEmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RequestScopeServiceTest {
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final RegisterEmployeeRepository accounts = mock(RegisterEmployeeRepository.class);
    private final RequestScopeService scope = new RequestScopeService(employees, accounts);
    @BeforeEach void accounts() {
        for (String id : List.of("EMP1","EMP2","EMP3")) { var a=new RegisterEmployee();a.setUserId(id);a.setRole("EMPLOYEE");when(accounts.findByUserId(id)).thenReturn(a); }
        for (String id : List.of("HR1","HR2","MGR")) { var a=new RegisterEmployee();a.setUserId(id);a.setRole(id.startsWith("HR")?"HR":"MANAGER");when(accounts.findByUserId(id)).thenReturn(a); }
    }
    @Test void staffRequestsOnlyReachAdminAndSelfApprovalIsDenied() {
        var own=expense(1,"HR1");var manager=expense(2,"MGR");var employee=expense(3,"EMP1");
        assertThrows(ResponseStatusException.class,()->scope.assertCanReview("HR","HR1",own));
        assertThrows(ResponseStatusException.class,()->scope.assertCanReview("HR","HR2",manager));
        assertThrows(ResponseStatusException.class,()->scope.assertCanReview("HR","HR2",own));
        assertDoesNotThrow(()->scope.assertCanReview("ADMIN","ADMIN1",own));
        assertDoesNotThrow(()->scope.assertCanReview("ADMIN","ADMIN1",manager));
        assertEquals(List.of(employee),scope.visibleTo("HR","HR2",List.of(own,manager,employee)));
        assertThrows(ResponseStatusException.class,()->scope.assertCanReview("HR","HR2",expense(9,"UNKNOWN")));
    }
    private ExpenseClaim expense(long id, String user) {
        var item = new ExpenseClaim(); item.setId(id); item.setUserId(user); return item;
    }
    @Test void managerSeesOnlyDirectReportsAndCannotReviewOthers() {
        when(employees.findUserIdsByReportingManager("MGR")).thenReturn(List.of("EMP1"));
        var direct = expense(1L,"EMP1"); var other = expense(2L,"EMP2");
        assertEquals(List.of(direct), scope.visibleTo("MANAGER","MGR",List.of(direct,other)));
        assertDoesNotThrow(() -> scope.assertCanReviewById("MANAGER","MGR",List.of(direct,other),1L));
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> scope.assertCanReviewById("MANAGER","MGR",List.of(direct,other),2L)).getStatusCode().value());
    }
    @Test void missingOwnerFailsClosedAndEmptyListDoesNotAuthorizeEmployee() {
        when(employees.findUserIdsByReportingManager("MGR")).thenReturn(List.of("EMP1"));
        assertThrows(ResponseStatusException.class, () -> scope.assertCanReview("MANAGER","MGR",new Object()));
        assertThrows(ResponseStatusException.class, () -> scope.visibleTo("EMPLOYEE","EMP1",List.of()));
    }
    @Test void selfServiceAccessDoesNotPermitSelfApproval() {
        var own = expense(1L,"MGR");
        when(employees.findUserIdsByReportingManager("MGR")).thenReturn(List.of("EMP1"));
        assertDoesNotThrow(() -> scope.assertCanAccessById("MANAGER","MGR",List.of(own),1L));
        assertThrows(ResponseStatusException.class, () -> scope.assertCanReview("MANAGER","MGR",own));
    }
    @Test void specializedOwnersAreResolvedWithoutUsingReviewerAsOwner() {
        var referral = new EmployeeReferral(); referral.setReferrerUserId("EMP1");
        referral.setReviewerUserId("HR");
        var incident = new WorkplaceIncident(); incident.setReporterUserId("EMP2");
        var feedback = new Employee360Feedback(); feedback.setSubjectUserId("EMP3"); feedback.setReviewerUserId("EMP4");
        assertEquals("EMP1",scope.ownerUserId(referral)); assertEquals("EMP2",scope.ownerUserId(incident));
        assertEquals("EMP3",scope.ownerUserId(feedback));
    }
    @Test void hrCanReviewAcrossTeamsAndEmployeeCannotReadAnotherLoan() {
        var item=expense(1L,"EMP2");
        assertDoesNotThrow(() -> scope.assertCanReview("HR","HR1",item));
        assertThrows(ResponseStatusException.class, () -> scope.assertCanAccessById("EMPLOYEE","EMP1",List.of(item),1L));
    }
    @Test void documentRecordsAndCandidateOffersKeepTheirDistinctOwnershipRules() {
        var document = new EmployeeManagementSystem.dto.EmployeeDocumentDTO(1L,"HR1","HR staff","IDENTITY","proof.pdf","application/pdf",1L,"PENDING",null,null,null,"HR1",null,null,null,null,false,false);
        assertEquals("HR1",scope.ownerUserId(document));
        assertEquals(List.of(),scope.visibleTo("HR","HR2",List.of(document)));
        assertEquals(List.of(document),scope.visibleTo("ADMIN","ADMIN1",List.of(document)));
        var offer=new EmployeeLetterRequest();offer.setId(2L);offer.setEmployeeUserId("HR1");offer.setLetterType("OFFER_LETTER");
        assertEquals(List.of(offer),scope.visibleTo("HR","HR1",List.of(offer)));
        assertThrows(ResponseStatusException.class,()->scope.assertCanReview("HR","HR1",offer));
        assertDoesNotThrow(()->scope.assertCanReview("HR","HR2",offer));
    }
}
