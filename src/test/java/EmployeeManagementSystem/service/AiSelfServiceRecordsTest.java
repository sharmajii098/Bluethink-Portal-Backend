package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSelfServiceRecordsTest {
    private final HrServiceRequestRepository hr = mock(HrServiceRequestRepository.class);
    private final TravelRequestRepository travel = mock(TravelRequestRepository.class);
    private final OvertimeRequestRepository overtime = mock(OvertimeRequestRepository.class);
    private final EmployeeDataChangeRequestRepository changes = mock(EmployeeDataChangeRequestRepository.class);
    private final EmployeeLetterRequestRepository letters = mock(EmployeeLetterRequestRepository.class);
    private final EmployeeDocumentRepository documents = mock(EmployeeDocumentRepository.class);
    private final AttendanceRegularizationRepository corrections = mock(AttendanceRegularizationRepository.class);
    private final EmployeeAccessCardRequestRepository cards = mock(EmployeeAccessCardRequestRepository.class);
    private final EmployeeRecognitionRepository recognitions = mock(EmployeeRecognitionRepository.class);
    private final EmployeeSuggestionRepository suggestions = mock(EmployeeSuggestionRepository.class);
    private final Employee signedIn = new Employee();
    private AiSelfServiceRecords service;

    @BeforeEach void setUp() {
        service = new AiSelfServiceRecords(hr, travel, overtime, changes, letters, documents,
                corrections, cards, recognitions, suggestions);
    }

    @Test void detectsOnlyExplicitModuleQuestions() {
        assertEquals(AiSelfServiceRecords.Topic.HR_REQUEST, AiSelfServiceRecords.detect("Show my HR service requests"));
        assertEquals(AiSelfServiceRecords.Topic.TRAVEL, AiSelfServiceRecords.detect("My travel requests"));
        assertEquals(AiSelfServiceRecords.Topic.OVERTIME, AiSelfServiceRecords.detect("Show overtime requests"));
        assertEquals(AiSelfServiceRecords.Topic.DATA_CHANGE, AiSelfServiceRecords.detect("My profile change requests"));
        assertEquals(AiSelfServiceRecords.Topic.LETTER, AiSelfServiceRecords.detect("Show letter requests"));
        assertEquals(AiSelfServiceRecords.Topic.DOCUMENT, AiSelfServiceRecords.detect("Show my document verification status"));
        assertEquals(AiSelfServiceRecords.Topic.ATTENDANCE_CORRECTION, AiSelfServiceRecords.detect("Show attendance regularization requests"));
        assertEquals(AiSelfServiceRecords.Topic.ACCESS_CARD, AiSelfServiceRecords.detect("My access card requests"));
        assertEquals(AiSelfServiceRecords.Topic.RECOGNITION, AiSelfServiceRecords.detect("My recognitions"));
        assertEquals(AiSelfServiceRecords.Topic.SUGGESTION, AiSelfServiceRecords.detect("My employee suggestions"));
        assertEquals(AiSelfServiceRecords.Topic.NONE, AiSelfServiceRecords.detect("How is the weather?"));
        assertEquals(AiSelfServiceRecords.Topic.NONE, AiSelfServiceRecords.detect("Please open my calendar"));
    }

    @Test void onlyAuthenticatedAccountIsQueried() {
        TravelRequest request = new TravelRequest();
        request.setDestination("Bangalore"); request.setStatus("PENDING");
        when(travel.findByUserIdOrderByStartDateDesc("EMP042")).thenReturn(List.of(request));
        String result = service.answer("Show my travel requests", "EMP042", "ROLE_EMPLOYEE", signedIn);
        assertTrue(result.contains("Bangalore"));
        verify(travel).findByUserIdOrderByStartDateDesc("EMP042");
        verify(travel, never()).findAll();
    }

    @Test void otherEmployeeRequestsAreDeniedBeforeAnyRepositoryAccess() {
        String result = service.answer("Show all employees' overtime requests", "EMP042", "ROLE_HR", signedIn);
        assertTrue(result.contains("only your own"));
        verifyNoInteractions(overtime);
    }

    @Test void refusesUnverifiedAccount() {
        assertThrows(AccessDeniedException.class, () -> service.answer("Show my documents", "", "ROLE_ADMIN", signedIn));
        verifyNoInteractions(documents);
    }

    @Test void documentContentsAndStoragePathsNeverReachAi() {
        EmployeeDocument document = new EmployeeDocument();
        document.setId(42L); document.setCategory("PERSONAL"); document.setStatus("VERIFIED");
        document.setOriginalFilename("sensitive-medical-filename.pdf");
        document.setStoredFilename("private-storage-key.pdf");
        document.setDescription("private description");
        when(documents.findByEmployeeIdOrderByUploadedAtDesc("EMP042")).thenReturn(List.of(document));
        String result = service.answer("Show my document status", "EMP042", "ROLE_EMPLOYEE", signedIn);
        assertTrue(result.contains("Document #42"));
        assertFalse(result.contains("sensitive-medical"));
        assertFalse(result.contains("private-storage-key"));
        assertFalse(result.contains("private description"));
    }

    @Test void privateDataChangeValuesAreNeverIncluded() {
        EmployeeDataChangeRequest change = new EmployeeDataChangeRequest();
        change.setFieldName("home address");
        change.setRequestedValue("SECRET REQUESTED ADDRESS");
        change.setCurrentValue("SECRET OLD ADDRESS");
        change.setStatus("SUBMITTED");
        when(changes.findByUserIdOrderByCreatedAtDesc("EMP042")).thenReturn(List.of(change));
        String result = service.answer("Show my data change requests", "EMP042", "ROLE_EMPLOYEE", signedIn);
        assertTrue(result.contains("home address"));
        assertFalse(result.contains("SECRET"));
    }

    @Test void letterPreviewDoesNotExposeSalaryOrPdf() {
        EmployeeLetterRequest letter = new EmployeeLetterRequest();
        letter.setRequestNumber("L-42"); letter.setLetterType("EXPERIENCE");
        letter.setAnnualCtc(new java.math.BigDecimal("999999"));
        letter.setPreparedSnapshot("private letter content");
        letter.setStatus("SUBMITTED");
        when(letters.findByEmployeeUserIdOrderByCreatedAtDesc("EMP042")).thenReturn(List.of(letter));
        String result = service.answer("Show my letter requests", "EMP042", "ROLE_EMPLOYEE", signedIn);
        assertTrue(result.contains("EXPERIENCE"));
        assertFalse(result.contains("999999"));
        assertFalse(result.contains("private letter"));
    }
}
