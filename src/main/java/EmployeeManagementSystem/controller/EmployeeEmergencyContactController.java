package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.service.RequestScopeService;
import EmployeeManagementSystem.entity.EmployeeEmergencyContact; import EmployeeManagementSystem.security.jwt.JwtUtil; import EmployeeManagementSystem.repository.EmployeeRepository; import EmployeeManagementSystem.service.EmployeeEmergencyContactService; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException; import java.util.*;
@RestController @RequestMapping("/api/emergency-contacts")
public class EmployeeEmergencyContactController{
 private final RequestScopeService scope;
 private final EmployeeEmergencyContactService service; private final JwtUtil jwt; private final EmployeeRepository employees;
 public EmployeeEmergencyContactController(EmployeeEmergencyContactService s,JwtUtil j,EmployeeRepository e,RequestScopeService requestScope){this.scope=requestScope;service=s;jwt=j;employees=e;}
 private String token(String a){if(a==null||!a.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required.");return a.substring(7);} private String user(String a){return jwt.extractUsername(token(a));} private String role(String a){String r=jwt.extractRole(token(a));return r==null?"":r.replace("ROLE_","").toUpperCase();} private boolean mgmt(String r){return Set.of("ADMIN","HR","MANAGER").contains(r);}
 private RuntimeException bad(Exception e){if(e instanceof SecurityException)return new ResponseStatusException(HttpStatus.FORBIDDEN,e.getMessage());return new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
 @GetMapping("/my") public List<EmployeeEmergencyContact> my(@RequestHeader("Authorization")String a){return service.my(user(a));}
 @GetMapping public List<EmployeeEmergencyContact> all(@RequestHeader("Authorization")String a){if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");return scope.visibleTo(role(a),user(a),service.all());}
 @PostMapping public EmployeeEmergencyContact create(@RequestHeader("Authorization")String a,@RequestBody EmployeeEmergencyContact x){String u=user(a);String n=employees.findByRegisterEmployee_UserId(u).map(e->e.getFullName()).orElse(u);try{return service.create(x,u,n);}catch(Exception e){throw bad(e);}}
 @PutMapping("/{id}") public EmployeeEmergencyContact update(@PathVariable Long id,@RequestHeader("Authorization")String a,@RequestBody EmployeeEmergencyContact x){scope.assertCanAccessById(role(a),user(a),service.all(),id);try{return service.update(id,x,user(a),mgmt(role(a)));}catch(Exception e){throw bad(e);}}
 @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id,@RequestHeader("Authorization")String a){scope.assertCanAccessById(role(a),user(a),service.all(),id);try{service.delete(id,user(a),mgmt(role(a)));return ResponseEntity.noContent().build();}catch(Exception e){throw bad(e);}}
 @PatchMapping("/{id}/verify") public EmployeeEmergencyContact verify(@PathVariable Long id,@RequestHeader("Authorization")String a){scope.assertCanReviewById(role(a),user(a),service.all(),id);if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");try{return service.verify(id,user(a));}catch(Exception e){throw bad(e);}}
}
