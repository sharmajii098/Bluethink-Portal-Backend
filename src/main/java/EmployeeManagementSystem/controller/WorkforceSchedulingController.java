package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.entity.*; import EmployeeManagementSystem.security.jwt.JwtUtil; import EmployeeManagementSystem.service.WorkforceSchedulingService; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException; import java.time.*; import java.util.*;
@RestController @RequestMapping("/api/workforce") public class WorkforceSchedulingController { private final EmployeeManagementSystem.service.RequestScopeService scope; private final WorkforceSchedulingService service; private final JwtUtil jwt; public WorkforceSchedulingController(WorkforceSchedulingService s,JwtUtil j,EmployeeManagementSystem.service.RequestScopeService scope){this.scope=scope;service=s;jwt=j;} private String token(String a){if(a==null||!a.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");return a.substring(7);} private String user(String a){return jwt.extractUsername(token(a));} private String role(String a){String r=jwt.extractRole(token(a));return r==null?"":r.replace("ROLE_","").toUpperCase();} private void manage(String a){if(!Set.of("ADMIN","HR","MANAGER").contains(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required");} private RuntimeException bad(Exception e){return new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage(),e);}
@GetMapping("/shifts") public List<Shift> shifts(@RequestHeader("Authorization") String a){try{return service.shifts();}catch(Exception e){throw bad(e);}} @PostMapping("/shifts") public Shift create(@RequestHeader("Authorization") String a,@RequestBody Shift s){manage(a);try{return service.createShift(s);}catch(Exception e){throw bad(e);}} @PutMapping("/shifts/{id}") public Shift updateShift(@RequestHeader("Authorization") String a,@PathVariable Long id,@RequestBody Shift s){manage(a);try{return service.updateShift(id,s);}catch(Exception e){throw bad(e);}} @DeleteMapping("/shifts/{id}") public Map<String,Object> deleteShift(@RequestHeader("Authorization") String a,@PathVariable Long id){manage(a);try{service.deleteShift(id);return Map.of("success",true);}catch(Exception e){throw bad(e);}}
private Map<String,Object> assignmentMap(ShiftAssignment a){
    Map<String,Object> out=new LinkedHashMap<>();
    out.put("id",a.getId());
    out.put("shiftDate",a.getShiftDate());
    out.put("status",a.getStatus());
    out.put("overtimeHours",a.getOvertimeHours());
    out.put("notes",a.getNotes());
    out.put("swapStatus",a.getSwapStatus());
    out.put("createdAt",a.getCreatedAt());

    Employee e=a.getEmployee();
    Map<String,Object> employee=new LinkedHashMap<>();
    if(e!=null){
        employee.put("id",e.getId());
        employee.put("fullName",e.getFullName());
        employee.put("email",e.getEmail());
        employee.put("userId",e.getRegisterEmployee()!=null?e.getRegisterEmployee().getUserId():(e.getEmployeeProfile()==null?null:e.getEmployeeProfile().getUserId()));
    }
    out.put("employee",employee);

    Shift s=a.getShift();
    Map<String,Object> shift=new LinkedHashMap<>();
    if(s!=null){
        shift.put("id",s.getId());
        shift.put("name",s.getName());
        shift.put("startTime",s.getStartTime());
        shift.put("endTime",s.getEndTime());
        shift.put("standardHours",s.getStandardHours());
        shift.put("description",s.getDescription());
        shift.put("overnight",s.getOvernight());
        shift.put("active",s.getActive());
    }
    out.put("shift",shift);
    return out;
}
@GetMapping("/my") public List<Map<String,Object>> my(@RequestHeader("Authorization") String a){try{return service.my(user(a)).stream().map(this::assignmentMap).toList();}catch(Exception e){throw bad(e);}}
@GetMapping("/all") public List<Map<String,Object>> all(@RequestHeader("Authorization") String a,@RequestParam String from,@RequestParam String to){manage(a);try{return scope.visibleTo(role(a),user(a),service.all(LocalDate.parse(from),LocalDate.parse(to))).stream().map(this::assignmentMap).toList();}catch(Exception e){throw bad(e);}}
@PostMapping("/assign") public Map<String,Object> assign(@RequestHeader("Authorization") String a,@RequestBody Map<String,String> b){manage(a);scope.assertCanManageUser(role(a),user(a),service.employeeUserId(Long.valueOf(b.get("employeeId"))));try{return assignmentMap(service.assign(Long.valueOf(b.get("employeeId")),Long.valueOf(b.get("shiftId")),LocalDate.parse(b.get("shiftDate")),b.get("notes")));}catch(Exception e){throw bad(e);}}
@PatchMapping("/{id}") public Map<String,Object> patch(@RequestHeader("Authorization") String a,@PathVariable Long id,@RequestBody Map<String,Object> b){manage(a);scope.assertCanReview(role(a),user(a),service.get(id));try{return assignmentMap(service.update(id,(String)b.get("status"),b.get("overtimeHours")==null?null:Double.valueOf(b.get("overtimeHours").toString()),(String)b.get("notes")));}catch(Exception e){throw bad(e);}}
@DeleteMapping("/{id}") public Map<String,Object> delete(@RequestHeader("Authorization") String a,@PathVariable Long id){manage(a);scope.assertCanReview(role(a),user(a),service.get(id));try{service.delete(id);return Map.of("success",true);}catch(Exception e){throw bad(e);}}
@PostMapping("/{id}/swap") public Map<String,Object> swap(@RequestHeader("Authorization") String a,@PathVariable Long id,@RequestBody Map<String,String> b){try{return assignmentMap(service.swap(id,user(a),LocalDate.parse(b.get("newDate"))));}catch(Exception e){throw bad(e);}}
}
