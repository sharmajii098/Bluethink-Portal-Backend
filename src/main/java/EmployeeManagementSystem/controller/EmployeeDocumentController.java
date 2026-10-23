package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.entity.EmployeeDocument;
import EmployeeManagementSystem.security.jwt.JwtUtil;
import EmployeeManagementSystem.repository.EmployeeProfileRepository;
import EmployeeManagementSystem.service.EmployeeDocumentService;

import org.springframework.http.HttpHeaders;
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

 public EmployeeDocumentController(
         EmployeeDocumentService service,
         EmployeeProfileRepository profiles,
         JwtUtil jwtUtil) {

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

  return service.list(
          username,
          mgmt(role(authorization))
  );
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

  return service.summary();
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
                   "attachment; filename=\"" + filename + "\""
           )
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

  try {

   service.delete(
           id,
           username,
           mgmt(role(authorization))
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