package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.entity.Policy;
import jakarta.validation.Valid;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.service.PolicyService;
import EmployeeManagementSystem.repository.RegisterEmployeeRepository;
import java.util.Locale;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api/policies")
public class PolicyController {
    private final PolicyService service; private final JwtUtil jwt; private final RegisterEmployeeRepository accounts;
    public PolicyController(PolicyService service, JwtUtil jwt, RegisterEmployeeRepository accounts){this.service=service;this.jwt=jwt;this.accounts=accounts;}
    private String token(String a){ if(a==null||!a.startsWith("Bearer ")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required."); return a.substring(7); }
    private String user(String a){ return jwt.extractUsername(token(a)); }
    private String role(String a){ String r=jwt.extractRole(token(a)); return r==null?"":r.replace("ROLE_","").toUpperCase(); }
    // A valid but stale HR/Admin JWT is not enough after a role reassignment.
    private String verifiedRole(String authHeader){
        String claimRole = role(authHeader);
        var account = accounts.findByUserId(user(authHeader));
        String liveRole = account == null || account.getRole() == null ? "" :
                account.getRole().replaceFirst("^ROLE_", "").toUpperCase(Locale.ROOT);
        if (!liveRole.equals(claimRole) || liveRole.isBlank())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Your access has changed. Sign out and sign in again.");
        return liveRole;
    }
    private void mgmt(String authHeader){ String current = verifiedRole(authHeader); if(!"ADMIN".equals(current)&&!"HR".equals(current)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required."); }
    @GetMapping public Object list(@RequestHeader("Authorization") String a){ return service.listForUser(user(a)); }
    @GetMapping("/all") public List<Policy> all(@RequestHeader("Authorization") String a){ mgmt(a); return service.listAll(); }
    @GetMapping("/{id}") public Policy get(@PathVariable Long id,@RequestHeader("Authorization") String a){ try{String current=verifiedRole(a);return ("ADMIN".equals(current)||"HR".equals(current)) ? service.get(id) : service.getVisible(id);}catch(NoSuchElementException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage());} }
    @PostMapping public Policy create(@RequestHeader("Authorization") String a,@Valid @RequestBody Policy p){mgmt(a);try{p.setId(null);return service.save(p);}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
    @PutMapping("/{id}") public Policy update(@PathVariable Long id,@RequestHeader("Authorization") String a,@Valid @RequestBody Policy p){mgmt(a);try{p.setId(id);return service.save(p);}catch(NoSuchElementException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage());}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
    @DeleteMapping("/{id}") public Map<String,Object> delete(@PathVariable Long id,@RequestHeader("Authorization") String a){mgmt(a);service.delete(id);return Map.of("success",true);}
    @PostMapping("/{id}/acknowledge") public Map<String,Object> acknowledge(@PathVariable Long id,@RequestHeader("Authorization") String a){try{return service.acknowledge(id,user(a));}catch(NoSuchElementException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage());}}
}
