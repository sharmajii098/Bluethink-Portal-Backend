package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.service.RequestScopeService;
import EmployeeManagementSystem.entity.HrServiceRequest;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.service.HrServiceRequestService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api/hr-service-requests")
public class HrServiceRequestController{
 private final HrServiceRequestService service; private final JwtUtil jwt; private final EmployeeRepository employees; private final RequestScopeService scope;
 public HrServiceRequestController(HrServiceRequestService s,JwtUtil j,EmployeeRepository e,RequestScopeService scope){this.scope=scope;service=s;jwt=j;employees=e;}
 private String token(String a){if(a==null||!a.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required.");return a.substring(7);}
 private String user(String a){return jwt.extractUsername(token(a));}
 private String role(String a){String r=jwt.extractRole(token(a));return r==null?"":r.replace("ROLE_","").toUpperCase();}
 private boolean mgmt(String r){return Set.of("ADMIN","HR","MANAGER").contains(r);}
 private RuntimeException bad(Exception e){if(e instanceof SecurityException)return new ResponseStatusException(HttpStatus.FORBIDDEN,e.getMessage());return new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
 @GetMapping("/my") public List<HrServiceRequest> my(@RequestHeader("Authorization")String a){return service.my(user(a));}
 @GetMapping public List<HrServiceRequest> all(@RequestHeader("Authorization")String a){if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");return scope.visibleTo(role(a),user(a),service.all());}
 @PostMapping public HrServiceRequest create(@RequestHeader("Authorization")String a,@RequestBody HrServiceRequest x){String u=user(a);String n=employees.findByRegisterEmployee_UserId(u).map(e->e.getFullName()).orElse(u);try{return service.create(x,u,n);}catch(Exception e){throw bad(e);}}
 @PatchMapping("/{id}/review") public HrServiceRequest review(@PathVariable Long id,@RequestHeader("Authorization")String a,@RequestParam String status,@RequestParam(required=false)String comment,@RequestParam(required=false)String assignedTo,@RequestParam(required=false)String assignedTeam,@RequestParam(required=false)String resolution){scope.assertCanReviewById(role(a),user(a),service.all(),id);if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");try{return service.review(id,user(a),status.toUpperCase(),comment,assignedTo,assignedTeam,resolution);}catch(Exception e){throw bad(e);}}
}
