package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.entity.StandupSettings;
import EmployeeManagementSystem.entity.StandupUpdate;
import EmployeeManagementSystem.repository.StandupSettingsRepository;
import EmployeeManagementSystem.repository.StandupUpdateRepository;
import EmployeeManagementSystem.repository.RegisterEmployeeRepository;
import EmployeeManagementSystem.entity.RegisterEmployee;
import EmployeeManagementSystem.entity.Timesheet;
import EmployeeManagementSystem.entity.Project;
import EmployeeManagementSystem.repository.TimesheetRepository;
import EmployeeManagementSystem.repository.ProjectRepository;
import EmployeeManagementSystem.repository.EmployeeProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import EmployeeManagementSystem.service.MeetingRules;
import EmployeeManagementSystem.service.StandupTimesheetPolicy;
import EmployeeManagementSystem.repository.EmployeeRepository;
import java.time.*;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/standup")
public class StandupController {
 private final StandupUpdateRepository updates; private final StandupSettingsRepository settings; private final RegisterEmployeeRepository employees; private final EmployeeRepository employeeRecords; private final TimesheetRepository timesheets; private final EmployeeProfileRepository profiles; private final ProjectRepository projects;
 public StandupController(StandupUpdateRepository u,StandupSettingsRepository s,RegisterEmployeeRepository e,EmployeeRepository records,TimesheetRepository timesheets,EmployeeProfileRepository profiles,ProjectRepository projects){updates=u;settings=s;employees=e;employeeRecords=records;this.timesheets=timesheets;this.profiles=profiles;this.projects=projects;}
 private String user(){Authentication a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");return a.getName();}
 private String role(){Authentication a=SecurityContextHolder.getContext().getAuthentication();if(a==null)return "EMPLOYEE";return a.getAuthorities().stream().map(x->x.getAuthority().replace("ROLE_","").toUpperCase(Locale.ROOT)).filter(Set.of("ADMIN","HR","MANAGER","EMPLOYEE")::contains).findFirst().orElse("EMPLOYEE");}
 private boolean canManage(){return Set.of("ADMIN","HR","MANAGER").contains(role());}
 private boolean canAccess(StandupUpdate update){return role().equals("ADMIN")||role().equals("HR")||update.getEmployeeUsername().equals(user())||("MANAGER".equals(role())&&employeeRecords.findUserIdsByReportingManager(user()).contains(update.getEmployeeUsername()));}
 private Long settingsId(){
  RegisterEmployee account=employees.findByUserId(user());
  if("MANAGER".equals(role())&&account!=null)return -account.getId();
  if("EMPLOYEE".equals(role()))return employeeRecords.findByRegisterEmployee_UserId(user()).map(e->e.getReportingManager()).map(e->e.getRegisterEmployee()).map(e->-e.getId()).orElse(1L);
  return 1L;
 }
 private StandupSettings settings(){Long id=settingsId();return settings.findById(id).orElseGet(()->settings.findById(1L).orElseGet(StandupSettings::new));}
 @GetMapping("/settings") @Transactional(readOnly=true) public Map<String,Object> getSettings(){StandupSettings s=settings();Map<String,Object> m=new LinkedHashMap<>();m.put("meetingUrl",s.getMeetingUrl());m.put("meetingTime",s.getMeetingTime());m.put("timezone",s.getTimezone());m.put("workingDays",s.getWorkingDays());m.put("remindersEnabled",s.isRemindersEnabled());m.put("canConfigure",canManage());m.put("scope","MANAGER".equals(role())?"Your direct team":"Organization / assigned team");return m;}
 public record SettingsRequest(String meetingUrl,String meetingTime,String timezone,String workingDays,Boolean remindersEnabled){}
 @PutMapping("/settings") @Transactional public Map<String,Object> putSettings(@RequestBody SettingsRequest r){
  if(!canManage())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Admin, HR or Manager permission required");
  try{
   String url=MeetingRules.link(r.meetingUrl());
   if(r.meetingTime()==null||!r.meetingTime().matches("^([01]\\d|2[0-3]):[0-5]\\d$"))throw new IllegalArgumentException("Use a valid meeting time (HH:mm).");
   MeetingRules.zone(r.timezone());MeetingRules.days(r.workingDays());
   Long id=settingsId();StandupSettings s=settings.findById(id).orElseGet(StandupSettings::new);s.setId(id);
   s.setMeetingUrl(url);s.setMeetingTime(r.meetingTime());s.setTimezone(r.timezone());s.setWorkingDays(r.workingDays().toUpperCase(Locale.ROOT).replace(" ",""));
   s.setRemindersEnabled(false);settings.save(s);return getSettings();
  }catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
 }
 private LocalDate today(){return LocalDate.now(MeetingRules.zone(settings().getTimezone()));}
 public record UpdateRequest(LocalDate workDate,String employeeName,String completedWork,String plannedWork,String blockers,String discussionPoints,Double hoursWorked,String workMode,Long projectId){}
 @GetMapping("/updates") @Transactional(readOnly=true) public List<StandupUpdate> listUpdates(){if(role().equals("ADMIN")||role().equals("HR"))return updates.findAllByOrderByWorkDateDescUpdatedAtDesc();if(role().equals("MANAGER"))return updates.findAllByOrderByWorkDateDescUpdatedAtDesc().stream().filter(this::canAccess).toList();return updates.findByEmployeeUsernameOrderByWorkDateDesc(user());}
 @GetMapping("/updates/mine") public Optional<StandupUpdate> mine(@RequestParam(required=false) LocalDate date){return updates.findByEmployeeUsernameAndWorkDate(user(),date==null?today():date);}
 @PostMapping("/updates") @Transactional public StandupUpdate saveUpdate(@RequestBody UpdateRequest r){if(r==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Update details are required");LocalDate date=r.workDate()==null?today():r.workDate();if(date.isAfter(today()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Updates cannot be submitted for a future date");StandupUpdate x=updates.findByEmployeeUsernameAndWorkDate(user(),date).orElseGet(StandupUpdate::new);x.setEmployeeUsername(user());RegisterEmployee actor=employees.findByUserId(user());x.setEmployeeName(actor==null?user():actor.getName());x.setWorkDate(date);x.setCompletedWork(clean(r.completedWork(),4000));x.setPlannedWork(clean(r.plannedWork(),4000));x.setBlockers(clean(r.blockers(),3000));x.setDiscussionPoints(clean(r.discussionPoints(),2000));if(x.getCompletedWork()==null&&x.getPlannedWork()==null&&x.getBlockers()==null&&x.getDiscussionPoints()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter at least one update.");if(x.getBlockerStatus()==null||"NONE".equals(x.getBlockerStatus()))x.setBlockerStatus(x.getBlockers()!=null&&!"none".equalsIgnoreCase(x.getBlockers())?"OPEN":"NONE");StandupUpdate saved=updates.saveAndFlush(x);saved.setTimesheetSyncStatus(syncTimesheet(saved,r));return saved;}
 /** Staff-entered actual hours only. Existing manual entries and reviewed logs are never rewritten. */
 private String syncTimesheet(StandupUpdate standup,UpdateRequest request){
  Double hours=request.hoursWorked();
  if(!StandupTimesheetPolicy.validHours(hours))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter actual hours worked (0.25-24, in quarter-hour increments) to save the stand-up and timesheet");
  String mode=clean(request.workMode(),30);
  if(mode==null||!Set.of("OFFICE","WFH","HYBRID").contains(mode.toUpperCase(Locale.ROOT)))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid work mode");
  String task=standup.getCompletedWork()!=null?standup.getCompletedWork():standup.getPlannedWork();
  if(task==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Completed work or a work plan is required to fill the timesheet");
  // Same payroll cutoff as /timesheets. Never backfill a locked pay period.
  if(!StandupTimesheetPolicy.withinCutoff(standup.getWorkDate(),LocalDate.now()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Timesheet cutoff passed; contact HR for the approved correction process");
  Timesheet existing=timesheets.findByStandupUpdateId(standup.getId()).orElse(null);
  if(existing==null&&!timesheets.findByEmployeeIdAndDate(user(),standup.getWorkDate()).isEmpty())return "EXISTING_MANUAL";
  if(existing!=null&&!StandupTimesheetPolicy.editable(existing.getStatus()))return "REVIEWED_UNCHANGED";
  Timesheet t=existing==null?new Timesheet():existing;
  if(existing==null){t.setStandupUpdateId(standup.getId());t.setEntrySource("STANDUP");t.setEmployeeId(user());t.setEmployeeName(standup.getEmployeeName());t.setDate(standup.getWorkDate());t.setEmployee(profiles.findByUserId(user()).orElse(null));}
  // Project assignment authorization is enforced here, not trusted from the browser.
  if(request.projectId()!=null){
   Project project=projects.findById(request.projectId()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Project not found"));
   var employee=employeeRecords.findByRegisterEmployee_UserId(user()).orElse(null);
   boolean projectManager=employee!=null&&Objects.equals(project.getProjectManagerId(),employee.getId());
   boolean assigned=employee!=null&&project.getAssignedEmployees()!=null&&project.getAssignedEmployees().stream().anyMatch(member->Objects.equals(member.getId(),employee.getId()));
   if(!projectManager&&!assigned)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Choose a project assigned to you or managed by you");
   t.setProject(project);t.setProjectName(project.getProjectName());
  }else{t.setProject(null);t.setProjectName(null);}
  t.setHoursWorked(hours);t.setWorkMode(mode.toUpperCase(Locale.ROOT));
  // Never invent a project when staff select general work.
  t.setTaskDescription(task.length()>1000?task.substring(0,1000):task);
  t.setStatus("PENDING");t.setApprovalComment(null);t.setManagerReviewedAt(null);t.setManagerReviewedBy(null);
  timesheets.save(t);
  return existing==null?"CREATED":"UPDATED";
 }
 public record ActionRequest(String blockerStatus,String actionOwner,LocalDate actionDueDate,String actionNotes){}
 @PatchMapping("/updates/{id}/action") @Transactional public StandupUpdate updateAction(@PathVariable Long id,@RequestBody ActionRequest r){if(!canManage())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Manager, HR or Admin permission required");StandupUpdate x=updates.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Update not found"));if(!canAccess(x))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You cannot manage this employee update");if(r.blockerStatus()!=null){String s=r.blockerStatus().toUpperCase(Locale.ROOT);if(!Set.of("NONE","OPEN","IN_PROGRESS","RESOLVED").contains(s))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid blocker status");x.setBlockerStatus(s);}String owner=clean(r.actionOwner(),120);if(owner!=null){RegisterEmployee target=employees.findByUserId(owner);if(target==null||("MANAGER".equals(role())&&!owner.equals(user())&&!employeeRecords.findUserIdsByReportingManager(user()).contains(owner)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose an action owner within your team.");}x.setActionOwner(owner);x.setActionDueDate(r.actionDueDate());x.setActionNotes(clean(r.actionNotes(),2000));return updates.save(x);}
 private String clean(String s,int max){if(s==null)return null;String v=s.trim();if(v.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A field exceeds its maximum length of "+max+" characters");return v.isEmpty()?null:v;}
}
