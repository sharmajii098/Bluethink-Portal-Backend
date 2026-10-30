package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.service.RequestScopeService;
import EmployeeManagementSystem.entity.EmployeeSuggestion;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.service.EmployeeSuggestionService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@RestController @RequestMapping("/api/employee-suggestions")
public class EmployeeSuggestionController{
 private final RequestScopeService scope;
 private final EmployeeSuggestionService service; private final JwtUtil jwt; private final EmployeeRepository employees;
 public EmployeeSuggestionController(EmployeeSuggestionService s,JwtUtil j,EmployeeRepository e,RequestScopeService requestScope){this.scope=requestScope;service=s;jwt=j;employees=e;}
 private String token(String a){if(a==null||!a.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required.");return a.substring(7);}
 private String user(String a){return jwt.extractUsername(token(a));}
 private String role(String a){String r=jwt.extractRole(token(a));return r==null?"":r.replace("ROLE_","").toUpperCase();}
 private boolean mgmt(String r){return Set.of("ADMIN","HR","MANAGER").contains(r);}
 private RuntimeException bad(Exception e){if(e instanceof SecurityException)return new ResponseStatusException(HttpStatus.FORBIDDEN,e.getMessage());return new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
 @GetMapping("/my") public List<EmployeeSuggestion> my(@RequestHeader("Authorization")String a){return service.my(user(a));}
 @GetMapping public List<EmployeeSuggestion> all(@RequestHeader("Authorization")String a){if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");return scope.visibleTo(role(a),user(a),service.all());}
 @PostMapping public EmployeeSuggestion create(@RequestHeader("Authorization")String a,@RequestBody EmployeeSuggestion x){String u=user(a);String n=employees.findByRegisterEmployee_UserId(u).map(e->e.getFullName()).orElse(u);try{return service.create(x,u,n);}catch(Exception e){throw bad(e);}}
 @PatchMapping("/{id}/review") public EmployeeSuggestion review(@PathVariable Long id,@RequestHeader("Authorization")String a,@RequestParam String status,@RequestParam(required=false)String response){scope.assertCanReviewById(role(a),user(a),service.all(),id);if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");try{return service.review(id,user(a),status.toUpperCase(),response);}catch(Exception e){throw bad(e);}}
}
