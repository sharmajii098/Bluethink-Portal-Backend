package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.entity.AiChatEntry;
import EmployeeManagementSystem.service.AiHistoryService;
import EmployeeManagementSystem.service.AiReportExportService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/ai")
public class AiV9Controller {
    private final AiHistoryService history;private final AiReportExportService exports;private final EmployeeManagementSystem.service.AiAuditService audit;
    public AiV9Controller(AiHistoryService history,AiReportExportService exports,EmployeeManagementSystem.service.AiAuditService audit){this.history=history;this.exports=exports;this.audit=audit;}
    public record Entry(long id,java.time.Instant createdAt,String question,String answer){
        static Entry of(AiChatEntry e){return new Entry(e.getId(),e.getCreatedAt(),e.getQuestion(),e.getAnswer());}
    }
    @GetMapping("/history") public List<Entry> history(@RequestParam(defaultValue="30") int limit){return history.recent(limit).stream().map(Entry::of).toList();}
    @GetMapping("/history/{id}") public Entry one(@PathVariable Long id){return Entry.of(history.get(id));}
    @DeleteMapping("/history") public Map<String,Long> clear(){return Map.of("deleted",history.clear());}
    @GetMapping("/reports/overview") public Map<String,String> overview(@RequestParam(defaultValue="") String question)throws IOException{return Map.of("report",audited("REPORT_OVERVIEW",()->exports.text(question)));}
    @GetMapping("/reports/overview.xlsx") public ResponseEntity<byte[]> xlsx(@RequestParam(defaultValue="") String question)throws IOException{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=Bluethink-HRMS-Overview.xlsx")
          .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(audited("REPORT_OVERVIEW_XLSX",()->exports.xlsx(question)));
    }
    @GetMapping("/reports/overview.pdf") public ResponseEntity<byte[]> pdf(@RequestParam(defaultValue="") String question)throws IOException{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=Bluethink-HRMS-Overview.pdf")
          .contentType(MediaType.APPLICATION_PDF).body(audited("REPORT_OVERVIEW_PDF",()->exports.pdf(question)));
    }

    @FunctionalInterface private interface ExportAction<T>{T run() throws IOException;}
    private <T> T audited(String event, ExportAction<T> action) throws IOException {
        try {T result=action.run();audit.record(event,"SUCCESS");return result;}
        catch(RuntimeException|IOException ex){audit.record(event,
            ex instanceof org.springframework.security.access.AccessDeniedException ? "DENIED" : "ERROR");throw ex;}
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException ex){return ResponseEntity.badRequest().body(Map.of("error",ex.getMessage()));}
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String,String>> denied(){return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","Report access denied"));}
}
