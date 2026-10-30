package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.entity.EmployeeDocument;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.repository.EmployeeProfileRepository;
import EmployeeManagementSystem.service.EmployeeDocumentService;
import EmployeeManagementSystem.service.RequestScopeService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/documents")
public class EmployeeDocumentController {

 private final EmployeeDocumentService service;
 private final EmployeeProfileRepository profiles;
 private final JwtUtil jwtUtil;
 private final RequestScopeService scope;

 public EmployeeDocumentController(
         EmployeeDocumentService service,
         EmployeeProfileRepository profiles,
         JwtUtil jwtUtil, RequestScopeService scope) {
  this.scope = scope;

  this.service = service;
  this.profiles = profiles;
  this.jwtUtil = jwtUtil;
 }


 // =========================================================
 // JWT HELPERS
 // =========================================================

 private String token(String auth) {

  if (auth == null || !auth.startsWith("Bearer ")) {
   throw new ResponseStatusException(
           HttpStatus.UNAUTHORIZED,
           "Authentication required."
   );
  }

  return auth.substring(7);
 }


 private String user(String auth) {
  return jwtUtil.extractUsername(token(auth));
 }


 private String role(String auth) {

  String r = jwtUtil.extractRole(token(auth));

  return r == null
          ? null
          : r.replace("ROLE_", "").toUpperCase();
 }


 private boolean mgmt(String role) {

  return "ADMIN".equals(role)
          || "HR".equals(role);
 }


 // =========================================================
 // LIST DOCUMENTS
 // =========================================================

 @GetMapping
 public Object list(
         @RequestHeader("Authorization") String authorization) {

  String username = user(authorization);

  return mgmt(role(authorization)) ? scope.visibleTo(role(authorization),username,service.list(username,true)) : service.list(username,false);
 }


 // =========================================================
 // CURRENT USER DOCUMENTS
 // =========================================================

 @GetMapping("/my")
 public Object myDocuments(
         @RequestHeader("Authorization") String authorization) {

  String username = user(authorization);
  return service.list(username, false);
 }


 // =========================================================
 // DOCUMENT SUMMARY - ADMIN / HR
 // =========================================================

 @GetMapping("/summary")
 public Object summary(
         @RequestHeader("Authorization") String authorization) {

  user(authorization);

  if (!mgmt(role(authorization))) {
   throw new ResponseStatusException(
           HttpStatus.FORBIDDEN,
           "Management access required."
   );
  }

  var documents = scope.visibleTo(role(authorization),user(authorization),service.list(user(authorization),true));
  return Map.of("total",documents.size(),"pending",documents.stream().filter(d->"PENDING".equals(d.status())).count(),
      "verified",documents.stream().filter(d->"VERIFIED".equals(d.status())).count(),
      "rejected",documents.stream().filter(d->"REJECTED".equals(d.status())).count(),
      "expired",documents.stream().filter(d->d.expired()).count(),
      "expiringSoon",documents.stream().filter(d->d.expiringSoon()).count());
 }


 // =========================================================
 // UPLOAD DOCUMENT
 // =========================================================

 @PostMapping(
         consumes = MediaType.MULTIPART_FORM_DATA_VALUE
 )
 public Object upload(
         @RequestHeader("Authorization") String authorization,
         @RequestParam MultipartFile file,
         @RequestParam String category,
         @RequestParam(required = false) String issueDate,
         @RequestParam(required = false) String expiryDate,
         @RequestParam(required = false) String description) {

  String username = user(authorization);

  String name = profiles
          .findByUserId(username)
          .map(p -> p.getFullName())
          .orElse(username);

  try {

   LocalDate parsedIssueDate =
           issueDate == null || issueDate.isBlank()
                   ? null
                   : LocalDate.parse(issueDate);

   LocalDate parsedExpiryDate =
           expiryDate == null || expiryDate.isBlank()
                   ? null
                   : LocalDate.parse(expiryDate);

   return service.upload(
           username,
           name,
           username,
           file,
           category,
           parsedIssueDate,
           parsedExpiryDate,
           description
   );

  } catch (Exception e) {

   throw new ResponseStatusException(
           HttpStatus.BAD_REQUEST,
           e.getMessage(),
           e
   );
  }
 }


 // =========================================================
 // DOWNLOAD DOCUMENT
 // =========================================================

 @GetMapping("/{id}/download")
 public ResponseEntity<byte[]> download(
         @PathVariable Long id,
         @RequestHeader("Authorization") String authorization) {

  String username = user(authorization);

  EmployeeDocument document =
          service.getOwned(
                  id,
                  username,
                  mgmt(role(authorization))
          );

  if (!username.equals(document.getEmployeeId())) scope.assertCanReview(role(authorization),username,document);

  try {

   String contentType =
           Optional.ofNullable(
                   document.getContentType()
           ).orElse(
                   MediaType.APPLICATION_OCTET_STREAM_VALUE
           );

   String filename =
           Optional.ofNullable(
                   document.getOriginalFilename()
           ).orElse("document");

   filename = filename.replace("\"", "");

   return ResponseEntity
           .ok()
           .contentType(
                   MediaType.parseMediaType(contentType)
           )
           .header(
                   HttpHeaders.CONTENT_DISPOSITION,
                   ContentDisposition.attachment().filename(filename.replaceAll("[\\p{Cntrl}]", "_"), StandardCharsets.UTF_8).build().toString()
           )
           .header("Cache-Control", "private, no-store")
           .header("X-Content-Type-Options", "nosniff")
           .body(service.bytes(document));

  } catch (Exception e) {

   throw new ResponseStatusException(
           HttpStatus.NOT_FOUND,
           "File not found."
   );
  }
 }


 // =========================================================
 // DELETE DOCUMENT
 // =========================================================

 @DeleteMapping("/{id}")
 public Object delete(
         @PathVariable Long id,
         @RequestHeader("Authorization") String authorization) {

  String username = user(authorization);

  EmployeeDocument document = service.getOwned(id,username,mgmt(role(authorization)));
  boolean reviewing = !username.equals(document.getEmployeeId());
  if (reviewing) scope.assertCanReview(role(authorization),username,document);
  try {

   service.delete(
           id,
           username,
           reviewing
   );

   return Map.of(
           "success",
           true
   );

  } catch (IllegalStateException e) {

   throw new ResponseStatusException(
           HttpStatus.BAD_REQUEST,
           e.getMessage()
   );
  }
 }


 // =========================================================
 // REVIEW DOCUMENT - ADMIN / HR
 // =========================================================

 @PutMapping("/{id}/review")
 public Object review(
         @PathVariable Long id,
         @RequestHeader("Authorization") String authorization,
         @RequestParam String status,
         @RequestParam(required = false) String comment) {

  String username = user(authorization);

  if (!mgmt(role(authorization))) {

   throw new ResponseStatusException(
           HttpStatus.FORBIDDEN,
           "Management access required."
   );
  }

  scope.assertCanReview(role(authorization),username,service.getOwned(id,username,true));
  try {

   return service.review(
           id,
           username,
           status,
           comment
   );

  } catch (Exception e) {

   throw new ResponseStatusException(
           HttpStatus.BAD_REQUEST,
           e.getMessage()
   );
  }
 }
}