package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.entity.StandupSettings;
import EmployeeManagementSystem.entity.StandupUpdate;
import EmployeeManagementSystem.repository.StandupSettingsRepository;
import EmployeeManagementSystem.repository.StandupUpdateRepository;
import EmployeeManagementSystem.repository.RegisterEmployeeRepository;
import EmployeeManagementSystem.entity.RegisterEmployee;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/standup")
public class StandupController {
 private final StandupUpdateRepository updates; private final StandupSettingsRepository settings; private final RegisterEmployeeRepository employees;
 public StandupController(StandupUpdateRepository u,StandupSettingsRepository s,RegisterEmployeeRepository e){updates=u;settings=s;employees=e;}
 private String user(){Authentication a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!a.isAuthenticated())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");return a.getName();}
 private String role(){Authentication a=SecurityContextHolder.getContext().getAuthentication();if(a==null)return "EMPLOYEE";return a.getAuthorities().stream().map(x->x.getAuthority().replace("ROLE_","").toUpperCase(Locale.ROOT)).filter(Set.of("ADMIN","HR","MANAGER","EMPLOYEE")::contains).findFirst().orElse("EMPLOYEE");}
 private boolean canManage(){return Set.of("ADMIN","HR","MANAGER").contains(role());}
 private boolean canAccess(StandupUpdate update){if(role().equals("ADMIN")||role().equals("HR"))return true;if(update.getEmployeeUsername().equalsIgnoreCase(user()))return true;if(!role().equals("MANAGER"))return false;RegisterEmployee manager=employees.findByUserId(user());if(manager==null)return false;return employees.findAll().stream().anyMatch(e->e.getReportingManagerId()!=null&&Objects.equals(e.getReportingManagerId(),manager.getId())&&e.getUserId().equalsIgnoreCase(update.getEmployeeUsername()));}
 private StandupSettings settings(){return settings.findById(1L).orElseGet(()->{StandupSettings s=new StandupSettings();s.setId(1L);return settings.save(s);});}
 @GetMapping("/settings") public Map<String,Object> getSettings(){StandupSettings s=settings();Map<String,Object> m=new LinkedHashMap<>();m.put("meetingUrl",s.getMeetingUrl());m.put("meetingTime",s.getMeetingTime());m.put("timezone",s.getTimezone());m.put("workingDays",s.getWorkingDays());m.put("remindersEnabled",s.isRemindersEnabled());m.put("canConfigure",role().equals("ADMIN"));return m;}
 public record SettingsRequest(String meetingUrl,String meetingTime,String timezone,String workingDays,Boolean remindersEnabled){}
 @PutMapping("/settings") public Map<String,Object> putSettings(@RequestBody SettingsRequest r){if(!role().equals("ADMIN"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only Admin can configure the stand-up schedule");if(r==null||r.meetingUrl()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Meeting URL is required");try{URI u=URI.create(r.meetingUrl().trim());if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||!(u.getHost().equalsIgnoreCase("meet.zoho.in")||u.getHost().endsWith(".zoho.com")||u.getHost().equalsIgnoreCase("zoho.com")))throw new Exception();}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use a valid HTTPS Zoho meeting URL");}StandupSettings s=settings();s.setMeetingUrl(r.meetingUrl().trim());if(r.meetingTime()!=null&&r.meetingTime().matches("^([01]\\d|2[0-3]):[0-5]\\d$"))s.setMeetingTime(r.meetingTime());if(r.timezone()!=null&&!r.timezone().isBlank())s.setTimezone(r.timezone().trim());if(r.workingDays()!=null&&!r.workingDays().isBlank())s.setWorkingDays(r.workingDays().trim());if(r.remindersEnabled()!=null)s.setRemindersEnabled(r.remindersEnabled());settings.save(s);return getSettings();}
 public record UpdateRequest(LocalDate workDate,String employeeName,String completedWork,String plannedWork,String blockers,String discussionPoints){}
 @GetMapping("/updates") @Transactional(readOnly=true) public List<StandupUpdate> listUpdates(){if(role().equals("ADMIN")||role().equals("HR"))return updates.findAllByOrderByWorkDateDescUpdatedAtDesc();if(role().equals("MANAGER"))return updates.findAllByOrderByWorkDateDescUpdatedAtDesc().stream().filter(this::canAccess).toList();return updates.findByEmployeeUsernameOrderByWorkDateDesc(user());}
 @GetMapping("/updates/mine") public Optional<StandupUpdate> mine(@RequestParam(required=false) LocalDate date){return updates.findByEmployeeUsernameAndWorkDate(user(),date==null?LocalDate.now():date);}
 @PostMapping("/updates") @Transactional public StandupUpdate saveUpdate(@RequestBody UpdateRequest r){if(r==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Update details are required");LocalDate date=r.workDate()==null?LocalDate.now():r.workDate();if(date.isAfter(LocalDate.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Updates cannot be submitted for a future date");StandupUpdate x=updates.findByEmployeeUsernameAndWorkDate(user(),date).orElseGet(StandupUpdate::new);x.setEmployeeUsername(user());x.setEmployeeName(clean(r.employeeName(),180));x.setWorkDate(date);x.setCompletedWork(clean(r.completedWork(),4000));x.setPlannedWork(clean(r.plannedWork(),4000));x.setBlockers(clean(r.blockers(),3000));x.setDiscussionPoints(clean(r.discussionPoints(),2000));if(x.getBlockerStatus()==null)x.setBlockerStatus("NONE");return updates.save(x);}
 public record ActionRequest(String blockerStatus,String actionOwner,LocalDate actionDueDate,String actionNotes){}
 @PatchMapping("/updates/{id}/action") @Transactional public StandupUpdate updateAction(@PathVariable Long id,@RequestBody ActionRequest r){if(!canManage())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Manager, HR or Admin permission required");StandupUpdate x=updates.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Update not found"));if(!canAccess(x))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You cannot manage this employee update");if(r.blockerStatus()!=null){String s=r.blockerStatus().toUpperCase(Locale.ROOT);if(!Set.of("NONE","OPEN","IN_PROGRESS","RESOLVED").contains(s))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid blocker status");x.setBlockerStatus(s);}x.setActionOwner(clean(r.actionOwner(),120));x.setActionDueDate(r.actionDueDate());x.setActionNotes(clean(r.actionNotes(),2000));return updates.save(x);}
 private String clean(String s,int max){if(s==null)return null;String v=s.trim();if(v.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A field exceeds its maximum length of "+max+" characters");return v.isEmpty()?null:v;}
}
