package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.service.RequestScopeService;

import EmployeeManagementSystem.entity.EmployeePrivacyConsent;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.service.EmployeePrivacyConsentService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/privacy-consents")
public class EmployeePrivacyConsentController {
 private final RequestScopeService scope;
    private final EmployeePrivacyConsentService service; private final JwtUtil jwt; private final EmployeeRepository employees;
    public EmployeePrivacyConsentController(EmployeePrivacyConsentService s,JwtUtil j,EmployeeRepository e,RequestScopeService requestScope){this.scope=requestScope;service=s;jwt=j;employees=e;}
    private String token(String a){if(a==null||!a.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required.");return a.substring(7);}
    private String user(String a){return jwt.extractUsername(token(a));}
    private String role(String a){String r=jwt.extractRole(token(a));return r==null?"":r.replace("ROLE_","").toUpperCase();}
    private boolean mgmt(String r){return Set.of("ADMIN","HR","MANAGER").contains(r);}
    @GetMapping("/my") public List<EmployeePrivacyConsent> my(@RequestHeader("Authorization") String a){return service.my(user(a));}
    @GetMapping public List<EmployeePrivacyConsent> all(@RequestHeader("Authorization") String a){if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");return scope.visibleTo(role(a),user(a),service.all());}
    @PostMapping public EmployeePrivacyConsent create(@RequestHeader("Authorization") String a,@RequestBody EmployeePrivacyConsent x){String u=user(a);String n=employees.findByRegisterEmployee_UserId(u).map(e->e.getFullName()).orElse(u);try{return service.create(x,u,n);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
    @PatchMapping("/{id}/decision") public EmployeePrivacyConsent decision(@PathVariable Long id,@RequestHeader("Authorization") String a,@RequestBody Map<String,String> body){scope.assertCanReviewById(role(a),user(a),service.all(),id);if(!mgmt(role(a)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Management access required.");try{return service.updateDecision(id,body.get("status"),body.get("comment"),user(a));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}}
}
