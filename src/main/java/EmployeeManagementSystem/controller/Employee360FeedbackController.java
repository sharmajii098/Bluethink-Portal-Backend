package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.service.RequestScopeService;
import EmployeeManagementSystem.entity.Employee360Feedback;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.service.Employee360FeedbackService;
import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;import java.util.*;
@RestController @RequestMapping("/api/employee-360-feedback")
public class Employee360FeedbackController{
 private final RequestScopeService scope;
 private final Employee360FeedbackService service;private final JwtUtil jwt;private final EmployeeRepository employees;
 public Employee360FeedbackController(Employee360FeedbackService s,JwtUtil j,EmployeeRepository e,RequestScopeService requestScope){this.scope=requestScope;service=s;jwt=j;employees=e;}
 private String user(String a){if(a==null||!a.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required.");return jwt.extractUsername(a.substring(7));}
 private String role(String a){String r=jwt.extractRole(a.substring(7));return r==null?"":r.replace("ROLE_","").toUpperCase();}
 private boolean mgmt(String r){return Set.of("ADMIN","HR","MANAGER").contains(r);}
 @GetMapping("/my") public List<Employee360Feedback> my(@RequestHeader("Authorization")String a){return service.my(user(a));}
 @GetMapping public List<Employee360Feedback> all(@RequestHeader("Authorization")String a){if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");return scope.visibleTo(role(a),user(a),service.all());}
 @PostMapping public Employee360Feedback create(@RequestHeader("Authorization")String a,@RequestBody Employee360Feedback x){String u=user(a);String n=employees.findByRegisterEmployee_UserId(u).map(e->e.getFullName()).orElse(u);String sn=employees.findByRegisterEmployee_UserId(x.getSubjectUserId()).map(e->e.getFullName()).orElse(x.getSubjectUserId());try{return service.create(x,u,n,sn);}catch(Exception e){throw new ResponseStatusException(e instanceof SecurityException?HttpStatus.FORBIDDEN:HttpStatus.BAD_REQUEST,e.getMessage());}}
 @PatchMapping("/{id}/review") public Employee360Feedback review(@PathVariable Long id,@RequestHeader("Authorization")String a,@RequestBody Map<String,String> body){scope.assertCanReviewById(role(a),user(a),service.all(),id);if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");try{return service.review(id,user(a),body.get("status"),body.get("comment"));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
}
