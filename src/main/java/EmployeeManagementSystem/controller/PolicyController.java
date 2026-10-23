package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.entity.Policy;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.service.PolicyService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api/policies")
public class PolicyController {
    private final PolicyService service; private final JwtUtil jwt;
    public PolicyController(PolicyService service, JwtUtil jwt){this.service=service;this.jwt=jwt;}
    private String token(String a){ if(a==null||!a.startsWith("Bearer ")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required."); return a.substring(7); }
    private String user(String a){ return jwt.extractUsername(token(a)); }
    private String role(String a){ String r=jwt.extractRole(token(a)); return r==null?"":r.replace("ROLE_","").toUpperCase(); }
    private void mgmt(String r){ if(!"ADMIN".equals(r)&&!"HR".equals(r)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required."); }
    @GetMapping public Object list(@RequestHeader("Authorization") String a){ return service.listForUser(user(a)); }
    @GetMapping("/all") public List<Policy> all(@RequestHeader("Authorization") String a){ mgmt(role(a)); return service.listAll(); }
    @GetMapping("/{id}") public Policy get(@PathVariable Long id,@RequestHeader("Authorization") String a){ try{return service.get(id);}catch(NoSuchElementException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage());} }
    @PostMapping public Policy create(@RequestHeader("Authorization") String a,@RequestBody Policy p){mgmt(role(a));try{p.setId(null);return service.save(p);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
    @PutMapping("/{id}") public Policy update(@PathVariable Long id,@RequestHeader("Authorization") String a,@RequestBody Policy p){mgmt(role(a));try{p.setId(id);return service.save(p);}catch(NoSuchElementException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage());}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
    @DeleteMapping("/{id}") public Map<String,Object> delete(@PathVariable Long id,@RequestHeader("Authorization") String a){mgmt(role(a));service.delete(id);return Map.of("success",true);}
    @PostMapping("/{id}/acknowledge") public Map<String,Object> acknowledge(@PathVariable Long id,@RequestHeader("Authorization") String a){try{return service.acknowledge(id,user(a));}catch(NoSuchElementException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage());}}
}
