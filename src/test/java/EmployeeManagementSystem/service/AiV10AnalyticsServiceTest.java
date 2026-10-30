package EmployeeManagementSystem.service;
import EmployeeManagementSystem.entity.Employee;
import EmployeeManagementSystem.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiV10AnalyticsServiceTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void employeeCannotReadTeamScope(){
  AiAssistantService ai=mock(AiAssistantService.class);
  EmployeeRepository employees=mock(EmployeeRepository.class);
  AttendanceRepository attendance=mock(AttendanceRepository.class);
  TimesheetRepository timesheets=mock(TimesheetRepository.class);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
    "EMP0004","",List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
  when(employees.findByRegisterEmployee_UserId("EMP0004")).thenReturn(Optional.of(new Employee()));
  AiV10AnalyticsService svc=new AiV10AnalyticsService(ai,employees,attendance,timesheets);
  assertThrows(AccessDeniedException.class,()->svc.report(LocalDate.of(2026,10,11),LocalDate.of(2026,10,17),"team"));
  verify(employees,never()).findAll();
  verify(attendance,never()).findAll();
  verify(timesheets,never()).findAll();
 }
 @Test void tooLongRangeRejectedBeforeEmployeeLookup(){
  AiAssistantService ai=mock(AiAssistantService.class);
  EmployeeRepository employees=mock(EmployeeRepository.class);
  // Date validation is reached only after authentication. The mocked AI
  // authorizer does not populate the SecurityContext on behalf of this test.
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
    "EMP0004","",List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
  AiV10AnalyticsService svc=new AiV10AnalyticsService(ai,employees,mock(AttendanceRepository.class),mock(TimesheetRepository.class));
  assertThrows(IllegalArgumentException.class,()->svc.report(LocalDate.of(2026,1,1),LocalDate.of(2026,2,5),"self"));
  verifyNoInteractions(employees);
 }
 @Test void unauthenticatedRequestIsDeniedBeforeDateValidationOrEmployeeLookup(){
  SecurityContextHolder.clearContext();
  AiAssistantService ai=mock(AiAssistantService.class);
  EmployeeRepository employees=mock(EmployeeRepository.class);
  AttendanceRepository attendance=mock(AttendanceRepository.class);
  TimesheetRepository timesheets=mock(TimesheetRepository.class);
  AiV10AnalyticsService svc=new AiV10AnalyticsService(ai,employees,attendance,timesheets);
  assertThrows(AccessDeniedException.class,()->svc.report(LocalDate.of(2026,1,1),LocalDate.of(2026,2,5),"self"));
  verifyNoInteractions(employees,attendance,timesheets);
 }
}
