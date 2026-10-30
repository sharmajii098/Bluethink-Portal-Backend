package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.LocalDate;
@RestController @RequestMapping("/api/ai/v10")
public class AiV10Controller {
 private final AiV10AnalyticsService analytics;private final AiReportExportService reports;private final AiAuditService audit;
 public AiV10Controller(AiV10AnalyticsService a,AiReportExportService r,AiAuditService audit){analytics=a;reports=r;this.audit=audit;}
 @GetMapping("/analytics")
 public AiV10AnalyticsService.Report analytics(@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="self") String scope){
  try {var result=analytics.report(from,to,scope);audit.record("ANALYTICS","SUCCESS");return result;}
  catch(RuntimeException ex){audit.record("ANALYTICS", ex instanceof org.springframework.security.access.AccessDeniedException ? "DENIED" : "ERROR_OR_INVALID");throw ex;}
 }
 @GetMapping("/reports/detailed.xlsx")
 public ResponseEntity<byte[]> xlsx(@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="self") String scope)throws IOException {
  try {byte[] data=reports.detailedXlsx(from,to,scope);audit.record("REPORT_XLSX","SUCCESS");return ResponseEntity.ok().cacheControl(CacheControl.noStore())
    .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=Bluethink-HRMS-Detailed.xlsx")
    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(data);}
  catch(RuntimeException|IOException ex){audit.record("REPORT_XLSX", ex instanceof org.springframework.security.access.AccessDeniedException ? "DENIED" : "ERROR");throw ex;}
 }
 @GetMapping("/reports/detailed.pdf")
 public ResponseEntity<byte[]> pdf(@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="self") String scope)throws IOException {
  try {byte[] data=reports.detailedPdf(from,to,scope);audit.record("REPORT_PDF","SUCCESS");return ResponseEntity.ok().cacheControl(CacheControl.noStore())
    .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=Bluethink-HRMS-Detailed.pdf")
    .contentType(MediaType.APPLICATION_PDF).body(data);}
  catch(RuntimeException|IOException ex){audit.record("REPORT_PDF", ex instanceof org.springframework.security.access.AccessDeniedException ? "DENIED" : "ERROR");throw ex;}
 }
 @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<java.util.Map<String,String>> invalid(IllegalArgumentException ex){return ResponseEntity.badRequest().body(java.util.Map.of("error",ex.getMessage()));}
 @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
 public ResponseEntity<java.util.Map<String,String>> denied(){return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("error","Report access denied"));}
}
