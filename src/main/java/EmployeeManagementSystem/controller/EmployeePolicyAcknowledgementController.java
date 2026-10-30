package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.service.RequestScopeService;
import EmployeeManagementSystem.entity.EmployeePolicyAcknowledgement;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.service.EmployeePolicyAcknowledgementService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@RestController @RequestMapping("/api/policy-acknowledgements")
public class EmployeePolicyAcknowledgementController{
 private final RequestScopeService scope;
 private final EmployeePolicyAcknowledgementService service; private final JwtUtil jwt; private final EmployeeRepository employees;
 public EmployeePolicyAcknowledgementController(EmployeePolicyAcknowledgementService s,JwtUtil j,EmployeeRepository e,RequestScopeService requestScope){this.scope=requestScope;service=s;jwt=j;employees=e;}
 private String token(String a){if(a==null||!a.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required.");return a.substring(7);}
 private String user(String a){return jwt.extractUsername(token(a));}
 private String role(String a){String r=jwt.extractRole(token(a));return r==null?"":r.replace("ROLE_","").toUpperCase();}
 private boolean mgmt(String r){return Set.of("ADMIN","HR","MANAGER").contains(r);}
 private RuntimeException bad(Exception e){if(e instanceof SecurityException)return new ResponseStatusException(HttpStatus.FORBIDDEN,e.getMessage());return new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
 @GetMapping("/my") public List<EmployeePolicyAcknowledgement> my(@RequestHeader("Authorization")String a){return service.my(user(a));}
 @GetMapping public List<EmployeePolicyAcknowledgement> all(@RequestHeader("Authorization")String a){if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");return scope.visibleTo(role(a),user(a),service.all());}
 @PostMapping public EmployeePolicyAcknowledgement create(@RequestHeader("Authorization")String a,@RequestBody EmployeePolicyAcknowledgement x){if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");try{String u=x.getUserId();if(u==null||u.isBlank())throw new IllegalArgumentException("Employee userId is required.");scope.assertCanManageUser(role(a),user(a),u);String n=employees.findByRegisterEmployee_UserId(u).map(e->e.getFullName()).orElse(u);return service.create(x,u,n);}catch(Exception e){throw bad(e);}}
 @PostMapping("/{id}/acknowledge") public EmployeePolicyAcknowledgement acknowledge(@PathVariable Long id,@RequestHeader("Authorization")String a,@RequestBody(required=false)Map<String,String> body){try{return service.acknowledge(id,user(a),body==null?null:body.get("note"));}catch(Exception e){throw bad(e);}}
 @PatchMapping("/{id}/status") public EmployeePolicyAcknowledgement status(@PathVariable Long id,@RequestHeader("Authorization")String a,@RequestParam String value){scope.assertCanReviewById(role(a),user(a),service.all(),id);if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");try{return service.updateStatus(id,value.toUpperCase(),user(a));}catch(Exception e){throw bad(e);}}
}
