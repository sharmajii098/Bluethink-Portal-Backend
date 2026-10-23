package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.entity.EmployeeLetterRequest;
import EmployeeManagementSystem.repository.EmployeeLetterRequestRepository;
import EmployeeManagementSystem.repository.EmployeeRepository;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.service.EmployeeLetterRequestService;
import EmployeeManagementSystem.service.HrLetterWorkflow;
import EmployeeManagementSystem.service.LetterPdfService;
import EmployeeManagementSystem.service.RequestScopeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/letter-requests")
public class EmployeeLetterRequestController {
    private final EmployeeLetterRequestService service;
    private final JwtUtil jwt;
    private final EmployeeRepository employees;
    private final EmployeeLetterRequestRepository letters;
    private final RequestScopeService scope;
    private final HrLetterWorkflow workflow;
    private final LetterPdfService pdf;

    public EmployeeLetterRequestController(
            EmployeeLetterRequestService service,
            JwtUtil jwt,
            EmployeeRepository employees,
            EmployeeLetterRequestRepository letters,
            RequestScopeService scope,
            HrLetterWorkflow workflow,
            LetterPdfService pdf) {
        this.service = service;
        this.jwt = jwt;
        this.employees = employees;
        this.letters = letters;
        this.scope = scope;
        this.workflow = workflow;
        this.pdf = pdf;
    }

    public record Offer(
            String candidateName,
            String designation,
            LocalDate joiningDate,
            LocalDate offerExpiresOn,
            BigDecimal annualCtc,
            Integer probationMonths,
            Integer noticeDays) {}

    private String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        return authorization.substring(7);
    }

    private String user(String authorization) {
        return jwt.extractUsername(token(authorization));
    }

    private String role(String authorization) {
        String value = jwt.extractRole(token(authorization));
        return value == null ? "" : value.replace("ROLE_", "").toUpperCase(Locale.ROOT);
    }

    private boolean management(String role) {
        return Set.of("ADMIN", "HR", "MANAGER").contains(role);
    }

    private <T> T validationResult(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping
    public List<EmployeeLetterRequest> all(@RequestHeader("Authorization") String authorization) {
        String currentRole = role(authorization);
        if (!management(currentRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Management access required.");
        }
        return scope.visibleTo(currentRole, user(authorization), service.all());
    }

    @GetMapping("/my")
    public List<EmployeeLetterRequest> my(@RequestHeader("Authorization") String authorization) {
        return service.my(user(authorization));
    }

    @PostMapping
    public EmployeeLetterRequest create(
            @RequestHeader("Authorization") String authorization,
            @RequestBody EmployeeLetterRequest request) {
        String currentUser = user(authorization);
        String name = employees.findByRegisterEmployee_UserId(currentUser)
                .map(employee -> employee.getFullName())
                .orElse(currentUser);
        return validationResult(() -> service.create(request, currentUser, name));
    }

    @PostMapping("/offers")
    public EmployeeLetterRequest createOffer(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Offer offer) {
        String currentRole = role(authorization);
        if (!Set.of("ADMIN", "HR").contains(currentRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HR or Admin access required.");
        }
        LocalDate today = LocalDate.now();
        if (offer == null
                || offer.candidateName() == null || offer.candidateName().isBlank() || offer.candidateName().length() > 200
                || offer.designation() == null || offer.designation().isBlank() || offer.designation().length() > 200
                || offer.joiningDate() == null || offer.joiningDate().isBefore(today)
                || offer.offerExpiresOn() == null || offer.offerExpiresOn().isBefore(today)
                || offer.annualCtc() == null || offer.annualCtc().signum() <= 0
                || offer.annualCtc().scale() > 2 || offer.annualCtc().precision() > 14
                || offer.probationMonths() == null || offer.probationMonths() < 0 || offer.probationMonths() > 60
                || offer.noticeDays() == null || offer.noticeDays() < 0 || offer.noticeDays() > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide valid candidate details and approved offer terms.");
        }

        EmployeeLetterRequest request = new EmployeeLetterRequest();
        request.setLetterType("OFFER_LETTER");
        request.setDesignation(offer.designation().trim());
        request.setJoiningDate(offer.joiningDate());
        request.setOfferExpiresOn(offer.offerExpiresOn());
        request.setAnnualCtc(offer.annualCtc());
        request.setProbationMonths(offer.probationMonths());
        request.setNoticeDays(offer.noticeDays());
        return validationResult(() ->
                service.create(request, user(authorization), offer.candidateName().trim()));
    }

    @PostMapping("/{id}/prepare")
    public EmployeeLetterRequest prepare(
            @PathVariable Long id,
            @RequestBody HrLetterWorkflow.Prepare input) {
        return validationResult(() -> workflow.prepare(id, input));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long id) {
        return pdfResponse(validationResult(() -> workflow.preview(id)), "draft-letter-" + id + ".pdf");
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> issuedPdf(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authorization) {
        EmployeeLetterRequest letter = letters.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Letter not found."));
        String currentRole = role(authorization);
        String currentUser = user(authorization);
        if (currentUser.equals(letter.getEmployeeUserId())) {
            if (!"ISSUED".equals(letter.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This letter has not been issued.");
            }
        } else if (management(currentRole)) {
            scope.assertCanReviewById(currentRole, currentUser, service.all(), id);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This letter belongs to another employee.");
        }
        return pdfResponse(pdf.generate(letter), "issued-letter-" + id + ".pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }

    @PatchMapping("/{id}/review")
    @Transactional
    public EmployeeLetterRequest review(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review details are required.");
        }
        String currentRole = role(authorization);
        if (!management(currentRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Management access required.");
        }
        String currentUser = user(authorization);
        scope.assertCanReviewById(currentRole, currentUser, service.all(), id);
        EmployeeLetterRequest letter = letters.lock(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Letter not found."));
        String status = String.valueOf(body.getOrDefault("status", "UNDER_REVIEW"));
        String comment = String.valueOf(body.getOrDefault("comment", ""));
        if (comment.length() > 1500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review comment is too long.");
        }
        String reviewerName = employees.findByRegisterEmployee_UserId(currentUser)
                .map(employee -> employee.getFullName())
                .orElse(currentUser);

        if ("APPROVED".equals(status)) {
            validationResult(() -> {
                workflow.approve(letter, currentUser);
                return letter;
            });
            letter.setStatus(status);
            letter.setReviewerUserId(currentUser);
            letter.setReviewerName(reviewerName);
            letter.setHrComment(comment);
            letter.setReviewedAt(LocalDateTime.now());
            return letters.save(letter);
        }
        if ("ISSUED".equals(status)) {
            validationResult(() -> {
                workflow.issue(letter);
                return letter;
            });
            letter.setStatus(status);
            letter.setReviewerUserId(currentUser);
            letter.setReviewerName(reviewerName);
            letter.setHrComment(comment);
            letter.setReviewedAt(LocalDateTime.now());
            return letters.save(letter);
        }
        return validationResult(() -> service.review(id, body, currentUser, reviewerName));
    }
}
