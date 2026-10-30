package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class StandupTimesheetSyncTest {
 private final StandupUpdateRepository updates=mock(StandupUpdateRepository.class);
 private final TimesheetRepository timesheets=mock(TimesheetRepository.class);
 private final ProjectRepository projects=mock(ProjectRepository.class);
 private final StandupController controller=new StandupController(updates,mock(StandupSettingsRepository.class),mock(RegisterEmployeeRepository.class),mock(EmployeeRepository.class),timesheets,mock(EmployeeProfileRepository.class),projects);
 @BeforeEach void login(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("EMP01",null,List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));when(updates.saveAndFlush(any())).thenAnswer(call->{StandupUpdate u=call.getArgument(0);org.springframework.test.util.ReflectionTestUtils.setField(u,"id",12L);return u;});}
 @AfterEach void logout(){SecurityContextHolder.clearContext();}
 private StandupController.UpdateRequest request(Long project){return new StandupController.UpdateRequest(LocalDate.now(),"untrusted name","Delivered feature",null,null,null,7.5,"OFFICE",project);}
 @Test void actualHoursCreatePendingWorklogOwnedByAuthenticatedAccount(){
  assertEquals("CREATED",controller.saveUpdate(request(null)).getTimesheetSyncStatus());
  var captor=org.mockito.ArgumentCaptor.forClass(Timesheet.class);verify(timesheets).save(captor.capture());var t=captor.getValue();assertEquals("EMP01",t.getEmployeeId());assertEquals(12L,t.getStandupUpdateId());assertEquals("STANDUP",t.getEntrySource());assertEquals(7.5,t.getHoursWorked());assertEquals("PENDING",t.getStatus());assertNull(t.getProject());
 }
 @Test void manuallyEnteredWorklogTakesPriority(){when(timesheets.findByEmployeeIdAndDate(eq("EMP01"),any())).thenReturn(List.of(new Timesheet()));assertEquals("EXISTING_MANUAL",controller.saveUpdate(request(null)).getTimesheetSyncStatus());verify(timesheets,never()).save(any());}
 @Test void reviewedGeneratedWorklogIsNeverRewritten(){var existing=new Timesheet();existing.setStatus("APPROVED");when(timesheets.findByStandupUpdateId(12L)).thenReturn(Optional.of(existing));assertEquals("REVIEWED_UNCHANGED",controller.saveUpdate(request(null)).getTimesheetSyncStatus());verify(timesheets,never()).save(any());}
 @Test void unassignedProjectIsRejected(){when(projects.findById(99L)).thenReturn(Optional.of(new Project()));assertThrows(ResponseStatusException.class,()->controller.saveUpdate(request(99L)));verify(timesheets,never()).save(any());}
}
