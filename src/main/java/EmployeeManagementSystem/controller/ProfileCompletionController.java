package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.entity.EmployeeComplianceRecord;
import EmployeeManagementSystem.entity.EmployeeEmergencyContact;
import EmployeeManagementSystem.entity.EmployeeProfile;
import EmployeeManagementSystem.repository.EmployeeComplianceRecordRepository;
import EmployeeManagementSystem.repository.EmployeeDocumentRepository;
import EmployeeManagementSystem.repository.EmployeeEducationRecordRepository;
import EmployeeManagementSystem.repository.EmployeeEmergencyContactRepository;
import EmployeeManagementSystem.repository.EmployeeFamilyMemberRepository;
import EmployeeManagementSystem.repository.EmployeeProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/profile-completion")
public class ProfileCompletionController {
    private final EmployeeManagementSystem.service.RequestScopeService scope;
    private final EmployeeProfileRepository profiles;
    private final EmployeeEmergencyContactRepository contacts;
    private final EmployeeComplianceRecordRepository compliance;
    private final EmployeeEducationRecordRepository education;
    private final EmployeeFamilyMemberRepository family;
    private final EmployeeDocumentRepository documents;

    public ProfileCompletionController(EmployeeProfileRepository profiles, EmployeeEmergencyContactRepository contacts,
                                       EmployeeComplianceRecordRepository compliance,
                                       EmployeeEducationRecordRepository education,
                                       EmployeeFamilyMemberRepository family,
                                       EmployeeDocumentRepository documents, EmployeeManagementSystem.service.RequestScopeService scope) {
        this.scope = scope;
        this.profiles = profiles;
        this.contacts = contacts;
        this.compliance = compliance;
        this.education = education;
        this.family = family;
        this.documents = documents;
    }

    @GetMapping("/my")
    public Map<String,Object> my(Authentication auth) {
        return build(profiles.findByUserId(auth.getName()).orElseThrow(() -> notFound("Profile")), auth.getName());
    }

    @GetMapping
    public List<Map<String,Object>> all(Authentication auth) {
        if (!management(auth)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Management access required.");
        String role = auth.getAuthorities().stream().map(x -> x.getAuthority().replace("ROLE_", ""))
                .findFirst().orElse("");
        return scope.visibleTo(role, auth.getName(), profiles.findAll()).stream()
                .map(p -> build(p, p.getUserId())).toList();
    }

    private Map<String,Object> build(EmployeeProfile p, String uid) {
        List<Map<String,Object>> checks = new ArrayList<>();
        check(checks,"Full name",p.getFullName()); check(checks,"Email",p.getEmail()); check(checks,"Phone number",p.getPhoneNumber());
        check(checks,"Date of birth",p.getDob()); check(checks,"Gender",p.getGender()); check(checks,"Blood group",p.getBloodGroup());
        check(checks,"Marital status",p.getMaritalStatus());
        add(checks,"Education history",!education.findByEmployeeIdOrderByEndDateDescIdDesc(uid).isEmpty());
        check(checks,"Bank account",p.getBankName(),p.getAccountNumber(),p.getIfscCode());
        check(checks,"Current address",p.getCurrentAddress()); check(checks,"Permanent address",p.getPermanentAddress());
        add(checks,"Family details",!family.findByEmployeeIdOrderByIdAsc(uid).isEmpty());
        var ownDocs = documents.findByEmployeeIdOrderByUploadedAtDesc(uid);
        add(checks,"Aadhaar card",ownDocs.stream().anyMatch(d -> "AADHAAR_CARD".equalsIgnoreCase(d.getCategory())));
        add(checks,"PAN card",ownDocs.stream().anyMatch(d -> "PAN_CARD".equalsIgnoreCase(d.getCategory())));
        add(checks,"Profile image",p.getPhoto()!=null && !p.getPhoto().isBlank());
        boolean emergency = contacts.findByUserIdOrderByPrimaryContactDescCreatedAtAsc(uid).stream().anyMatch(c -> Boolean.TRUE.equals(c.getPrimaryContact()) || Boolean.TRUE.equals(c.getVerified()));
        add(checks,"Emergency contact",emergency);
        boolean complianceOk = compliance.findByUserIdOrderByExpiryDateAsc(uid).stream().anyMatch(c -> "VERIFIED".equals(c.getStatus()) && (c.getExpiryDate()==null || !c.getExpiryDate().isBefore(java.time.LocalDate.now())));
        add(checks,"Verified compliance record",complianceOk);
        long completed = checks.stream().filter(x -> Boolean.TRUE.equals(x.get("completed"))).count();
        int percent = checks.isEmpty()?0:(int)Math.round(completed*100.0/checks.size());
        Map<String,Object> out = new LinkedHashMap<>(); out.put("userId",uid); out.put("employeeName",p.getFullName()); out.put("department",p.getDepartment()); out.put("designation",p.getDesignation()); out.put("completionPercent",percent); out.put("completedItems",completed); out.put("totalItems",checks.size()); out.put("checks",checks); return out;
    }
    private void check(List<Map<String,Object>> c,String label,Object... vals){ boolean ok=Arrays.stream(vals).allMatch(v -> v!=null && (! (v instanceof String) || !((String)v).isBlank())); add(c,label,ok); }
    private void add(List<Map<String,Object>> c,String label,boolean ok){c.add(Map.of("label",label,"completed",ok));}
    private boolean management(Authentication a){return a.getAuthorities().stream().map(x->x.getAuthority()).anyMatch(x->Set.of("ROLE_ADMIN","ROLE_HR","ROLE_MANAGER").contains(x));}
    private ResponseStatusException notFound(String s){return new ResponseStatusException(HttpStatus.NOT_FOUND,s+" not found.");}
}
