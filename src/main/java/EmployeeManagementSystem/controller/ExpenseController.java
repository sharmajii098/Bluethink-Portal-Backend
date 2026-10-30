package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.service.RequestScopeService;
import EmployeeManagementSystem.entity.ExpenseClaim; import EmployeeManagementSystem.security.jwt.JwtUtil; import EmployeeManagementSystem.repository.EmployeeRepository; import EmployeeManagementSystem.service.ExpenseService;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException; import java.util.*;
@RestController @RequestMapping("/api/expenses")
public class ExpenseController {
 private final RequestScopeService scope;
 private final ExpenseService service; private final JwtUtil jwt; private final EmployeeRepository employees;
 public ExpenseController(ExpenseService s,JwtUtil j,EmployeeRepository e,RequestScopeService requestScope){this.scope=requestScope;service=s;jwt=j;employees=e;}
 private String tok(String a){if(a==null||!a.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required.");return a.substring(7);} private String user(String a){return jwt.extractUsername(tok(a));} private String role(String a){String r=jwt.extractRole(tok(a));return r==null?"":r.replace("ROLE_","").toUpperCase();} private void mgmt(String r){if(!"ADMIN".equals(r)&&!"HR".equals(r)&&!"MANAGER".equals(r))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");}
 @GetMapping("/my") public List<ExpenseClaim> my(@RequestHeader("Authorization")String a){return service.my(user(a));}
 @GetMapping("/all") public List<ExpenseClaim> all(@RequestHeader("Authorization")String a){mgmt(role(a));return scope.visibleTo(role(a),user(a),service.all());}
 @PostMapping public ExpenseClaim create(@RequestHeader("Authorization")String a,@RequestBody ExpenseClaim e){String u=user(a);String n=employees.findByRegisterEmployee_UserId(u).map(x->x.getFullName()).orElse(u);try{return service.create(e,u,n);}catch(Exception x){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,x.getMessage());}}
 @PutMapping("/{id}") public ExpenseClaim update(@PathVariable Long id,@RequestHeader("Authorization")String a,@RequestBody ExpenseClaim e){try{return service.update(id,e,user(a));}catch(NoSuchElementException x){throw new ResponseStatusException(HttpStatus.NOT_FOUND,x.getMessage());}catch(Exception x){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,x.getMessage());}}
 @PatchMapping("/{id}/review") public ExpenseClaim review(@PathVariable Long id,@RequestHeader("Authorization")String a,@RequestBody Map<String,Object>b){scope.assertCanReviewById(role(a),user(a),service.all(),id);mgmt(role(a));try{return service.review(id,String.valueOf(b.get("status")),String.valueOf(b.getOrDefault("comment","")),user(a));}catch(Exception x){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,x.getMessage());}}
 @DeleteMapping("/{id}") public Map<String,Object> delete(@PathVariable Long id,@RequestHeader("Authorization")String a){try{service.delete(id,user(a));return Map.of("success",true);}catch(Exception x){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,x.getMessage());}}
}
