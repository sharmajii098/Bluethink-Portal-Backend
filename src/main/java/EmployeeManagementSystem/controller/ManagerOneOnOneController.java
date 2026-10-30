package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.service.RequestScopeService;
import EmployeeManagementSystem.entity.ManagerOneOnOne;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.service.ManagerOneOnOneService;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException; import java.util.*;
@RestController @RequestMapping("/api/manager-one-on-ones")
public class ManagerOneOnOneController{
 private final RequestScopeService scope;
 private final ManagerOneOnOneService service; private final JwtUtil jwt; private final EmployeeRepository employees;
 public ManagerOneOnOneController(ManagerOneOnOneService s,JwtUtil j,EmployeeRepository e,RequestScopeService requestScope){this.scope=requestScope;service=s;jwt=j;employees=e;}
 private String token(String a){if(a==null||!a.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required.");return a.substring(7);}
 private String user(String a){return jwt.extractUsername(token(a));} private String role(String a){String r=jwt.extractRole(token(a));return r==null?"":r.replace("ROLE_","").toUpperCase();}
 private boolean mgmt(String r){return Set.of("ADMIN","HR","MANAGER").contains(r);}
 @GetMapping("/my") public List<ManagerOneOnOne> my(@RequestHeader("Authorization")String a){return service.my(user(a));}
 @GetMapping public List<ManagerOneOnOne> all(@RequestHeader("Authorization")String a){if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");return scope.visibleTo(role(a),user(a),service.all());}
 @PostMapping public ManagerOneOnOne create(@RequestHeader("Authorization")String a,@RequestBody ManagerOneOnOne x){scope.assertCanManageUser(role(a),user(a),x.getEmployeeUserId());String u=user(a);String n=employees.findByRegisterEmployee_UserId(u).map(e->e.getFullName()).orElse(u);try{return service.create(x,u,n);}catch(Exception e){throw new ResponseStatusException(e instanceof SecurityException?HttpStatus.FORBIDDEN:HttpStatus.BAD_REQUEST,e.getMessage());}}
 @PatchMapping("/{id}") public ManagerOneOnOne update(@PathVariable Long id,@RequestHeader("Authorization")String a,@RequestBody ManagerOneOnOne x){scope.assertCanReviewById(role(a),user(a),service.all(),id);try{return service.update(id,x,user(a),role(a));}catch(Exception e){throw new ResponseStatusException(e instanceof SecurityException?HttpStatus.FORBIDDEN:HttpStatus.BAD_REQUEST,e.getMessage());}}
}
