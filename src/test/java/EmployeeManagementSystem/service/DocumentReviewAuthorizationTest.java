package EmployeeManagementSystem.service;

import EmployeeManagementSystem.controller.EmployeeDocumentController;
import EmployeeManagementSystem.entity.EmployeeDocument;
import EmployeeManagementSystem.repository.EmployeeProfileRepository;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentReviewAuthorizationTest {
    @Test void deniedScopeDoesNotReachTheDocumentMutationAndRemainsForbidden() {
        var service=mock(EmployeeDocumentService.class);
        var profiles=mock(EmployeeProfileRepository.class);
        var jwt=mock(JwtUtil.class);
        var scope=mock(RequestScopeService.class);
        var controller=new EmployeeDocumentController(service,profiles,jwt,scope);
        var document=EmployeeDocument.builder().employeeId("MANAGER01").build();
        when(jwt.extractUsername("token")).thenReturn("HR01");
        when(jwt.extractRole("token")).thenReturn("HR");
        when(service.getOwned(7L,"HR01",true)).thenReturn(document);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"Admin required"))
                .when(scope).assertCanReview("HR","HR01",document);
        var denial=assertThrows(ResponseStatusException.class,()->controller.review(7L,"Bearer token","VERIFIED","Checked"));
        assertEquals(403,denial.getStatusCode().value());
        verify(service,never()).review(anyLong(),anyString(),anyString(),anyString());
    }
}
