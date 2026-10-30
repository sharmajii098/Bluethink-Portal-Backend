package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.service.RequestScopeService;
import EmployeeManagementSystem.entity.ProbationReview; import EmployeeManagementSystem.security.jwt.JwtUtil; import EmployeeManagementSystem.repository.EmployeeRepository; import EmployeeManagementSystem.service.ProbationReviewService;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException; import java.util.*;
@RestController @RequestMapping("/api/probation-reviews")
public class ProbationReviewController{
 private final RequestScopeService scope;
 private final ProbationReviewService service; private final JwtUtil jwt; private final EmployeeRepository employees;
 public ProbationReviewController(ProbationReviewService s,JwtUtil j,EmployeeRepository e,RequestScopeService requestScope){this.scope=requestScope;service=s;jwt=j;employees=e;}
 private String t(String a){if(a==null||!a.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required.");return a.substring(7);} private String u(String a){return jwt.extractUsername(t(a));} private String r(String a){String x=jwt.extractRole(t(a));return x==null?"":x.replace("ROLE_","").toUpperCase();} private boolean m(String r){return Set.of("ADMIN","HR","MANAGER").contains(r);}
 @GetMapping public List<ProbationReview> all(@RequestHeader("Authorization") String a){if(!m(r(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");return scope.visibleTo(r(a),u(a),service.all());}
 @GetMapping("/my") public List<ProbationReview> my(@RequestHeader("Authorization") String a){return service.my(u(a));}
 @PostMapping public ProbationReview create(@RequestHeader("Authorization") String a,@RequestBody ProbationReview x){String id=u(a);String n=employees.findByRegisterEmployee_UserId(id).map(EmployeeManagementSystem.entity.Employee::getFullName).orElse(id);try{return service.create(x,id,n);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
 @PatchMapping("/{id}/review") public ProbationReview review(@RequestHeader("Authorization") String a,@PathVariable Long id,@RequestBody Map<String,Object>b){scope.assertCanReviewById(r(a),u(a),service.all(),id);if(!m(r(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");try{return service.review(id,String.valueOf(b.get("status")),String.valueOf(b.getOrDefault("decision","")),b.get("rating")==null?null:Integer.valueOf(String.valueOf(b.get("rating"))),String.valueOf(b.getOrDefault("comment","")),u(a));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
}
