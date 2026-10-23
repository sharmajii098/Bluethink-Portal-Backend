package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.aiservice.ChatService;
import EmployeeManagementSystem.aiservice.DocumentIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final ChatService chatService;
    private final DocumentIngestionService documentIngestionService;

    public AiChatController(ChatService chatService, DocumentIngestionService documentIngestionService) {
        this.chatService = chatService;
        this.documentIngestionService = documentIngestionService;
    }

    @PostMapping("/documents/ingest")
    public ResponseEntity<Integer> ingestPdf() {
        return ResponseEntity.ok(documentIngestionService.ingestPdf());
    }

    @GetMapping("/chat")
    public ResponseEntity<String> chat(
            @RequestParam(value = "query", required = true) String query
            //   @RequestHeader("userId") String userId
    ) {
        return ResponseEntity.ok(chatService.getResponse(query));
    }
}