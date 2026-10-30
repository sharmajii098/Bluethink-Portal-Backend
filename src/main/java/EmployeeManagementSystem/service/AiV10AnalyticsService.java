package EmployeeManagementSystem.service;
import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
@Service
public class AiV10AnalyticsService {
 private final AiAssistantService ai;
 private final EmployeeRepository employees;
 private final AttendanceRepository attendance;
 private final TimesheetRepository timesheets;
 public AiV10AnalyticsService(AiAssistantService ai,EmployeeRepository employees, AttendanceRepository attendance, TimesheetRepository timesheets){
  this.ai=ai;this.employees=employees;this.attendance=attendance;this.timesheets=timesheets;
 }
 public record Report(LocalDate start,LocalDate end,String scope,int employees,int attendanceEntries,
  Map<String,Long> attendanceStatuses,int timesheetEntries,double loggedHours,
  Map<String,Long> timesheetStatuses,List<Map<String,String>> rows){}
 public Report report(LocalDate start,LocalDate end,String scope){
  // Reuse exact role validation from existing V9.2 report entrypoint.
  ai.authorizedOverview("");
  Authentication auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth==null || !auth.isAuthenticated()) throw new AccessDeniedException("Login required");
  if(start==null||end==null||start.isAfter(end)||start.plusDays(30).isBefore(end))
   throw new IllegalArgumentException("Choose a valid date range of at most 31 days");
  String requested=scope==null?"self":scope.trim().toLowerCase(Locale.ROOT);
  if(!Set.of("self","team").contains(requested)) throw new IllegalArgumentException("Invalid report scope");
  Employee self=employees.findByRegisterEmployee_UserId(auth.getName())
    .or(() -> employees.findByEmployeeProfile_UserId(auth.getName())).orElseThrow(()->new AccessDeniedException("No linked employee"));
  Set<String> role=auth.getAuthorities().stream().map(a->a.getAuthority().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
  List<Employee> people;
  if(requested.equals("team")){
   if(!role.contains("ROLE_MANAGER")) throw new AccessDeniedException("Team report requires Manager role");
   people=employees.findByReportingManager_Id(self.getId());
  } else people=List.of(self);
  Map<String,Long> attendanceStatus=new TreeMap<>(),timesheetStatus=new TreeMap<>();
  List<Map<String,String>> rows=new ArrayList<>();int attendanceCount=0,sheetCount=0;double hours=0;
  for(Employee e:people){
   String id=e.getRegisterEmployee()!=null?e.getRegisterEmployee().getUserId():null;
   if(id==null && e.getEmployeeProfile()!=null)id=e.getEmployeeProfile().getUserId();
   if(id==null)continue;
   for(Attendance a:attendance.findByEmployeeAndAiDateRange(e,start,end)){
    LocalDate day=a.getDate()!=null?a.getDate():a.getAttendanceDate();
    if(day==null)continue;
    String status=a.getStatus()==null?"UNKNOWN":a.getStatus().name();
    attendanceStatus.merge(status,1L,Long::sum);attendanceCount++;
    rows.add(row(id,e.getFullName(),"ATTENDANCE",day.toString(),status,"","",""));
   }
   for(Timesheet t:timesheets.findByEmployeeIdAndDateBetween(id,start,end)){
    String status=t.getStatus()==null?"UNKNOWN":t.getStatus().toUpperCase(Locale.ROOT);
    timesheetStatus.merge(status,1L,Long::sum);sheetCount++;
    if(t.getHoursWorked()!=null)hours+=t.getHoursWorked();
    rows.add(row(id,e.getFullName(),"TIMESHEET",String.valueOf(t.getDate()),status,
      t.getHoursWorked()==null?"":String.valueOf(t.getHoursWorked()),t.getProjectName(),t.getTaskDescription()));
   }
  }
  rows.sort(Comparator.comparing((Map<String,String> r)->r.get("date")).reversed());
  return new Report(start,end,requested,people.size(),attendanceCount,Map.copyOf(attendanceStatus),
    sheetCount,Math.round(hours*100.0)/100.0,Map.copyOf(timesheetStatus),List.copyOf(rows));
 }
 private Map<String,String> row(String id,String name,String type,String day,String status,String hours,String project,String task){
  Map<String,String> r=new LinkedHashMap<>();r.put("employeeId",id);r.put("employeeName",safe(name));
  r.put("module",type);r.put("date",day);r.put("status",status);r.put("hours",hours);
  r.put("project",safe(project));r.put("task",safe(task));return Collections.unmodifiableMap(r);
 }
 private String safe(String s){return s==null?"":s.replaceAll("[\r\n\t]"," ").substring(0,Math.min(200,s.length()));}
}
