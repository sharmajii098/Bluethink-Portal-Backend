package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.entity.TalentSuccessionPlan;
import EmployeeManagementSystem.service.TalentSuccessionPlanService;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/talent-succession")
public class TalentSuccessionPlanController{
    private final TalentSuccessionPlanService service; private final JwtUtil jwt;
    public TalentSuccessionPlanController(TalentSuccessionPlanService s,JwtUtil j){service=s;jwt=j;}
    private String token(String a){if(a==null||!a.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required.");return a.substring(7);}
    private String user(String a){return jwt.extractUsername(token(a));}
    private String role(String a){String r=jwt.extractRole(token(a));return r==null?"":r.replace("ROLE_","").toUpperCase();}
    private void management(String a){if(!Set.of("ADMIN","HR","MANAGER").contains(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");}
    private ResponseStatusException bad(Exception e){return new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage(),e);}
    @GetMapping public List<TalentSuccessionPlan> all(@RequestHeader("Authorization") String a){management(a);return service.all();}
    @GetMapping("/my") public List<TalentSuccessionPlan> mine(@RequestHeader("Authorization") String a){return service.mine(user(a));}
    @GetMapping("/employee/my") public List<TalentSuccessionPlan> employeeMine(@RequestHeader("Authorization") String a){return service.mine(user(a));}
    @PostMapping public TalentSuccessionPlan create(@RequestHeader("Authorization") String a,@RequestBody TalentSuccessionPlan p){management(a);try{return service.create(p,user(a));}catch(Exception e){throw bad(e);}}
    @PutMapping("/{id}") public TalentSuccessionPlan update(@RequestHeader("Authorization") String a,@PathVariable Long id,@RequestBody TalentSuccessionPlan p){management(a);try{return service.update(id,p,user(a));}catch(Exception e){throw bad(e);}}
    @PatchMapping("/{id}/status") public TalentSuccessionPlan status(@RequestHeader("Authorization") String a,@PathVariable Long id,@RequestBody Map<String,String> body){management(a);try{return service.review(id,body.get("status"),body.get("comment"),user(a));}catch(Exception e){throw bad(e);}}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@RequestHeader("Authorization") String a,@PathVariable Long id){management(a);try{service.delete(id);}catch(Exception e){throw bad(e);}}
}
