package EmployeeManagementSystem.controller;
import EmployeeManagementSystem.service.AiKnowledgeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
@RestController @RequestMapping("/api/ai/knowledge") public class AiKnowledgeController {
 private final AiKnowledgeService service;public AiKnowledgeController(AiKnowledgeService service){this.service=service;}
 @GetMapping("/status") public Object status(){return service.status();}
 @GetMapping("/sources") public Object sources(){return service.sources();}
 @PostMapping(value="/sources",consumes="multipart/form-data") public Object publish(@RequestPart("file")MultipartFile file,@RequestParam String title,@RequestParam String roles,@RequestParam(required=false)Long departmentId,@RequestParam(defaultValue="false")boolean approved)throws IOException{return service.publish(file,title,roles,departmentId,approved);}
 @DeleteMapping("/sources/{id}") public void unpublish(@PathVariable String id){service.unpublish(id);}
 @PostMapping("/vector/reindex") public Object reindex(@RequestParam(defaultValue="false")boolean confirmed){return service.reindex(confirmed);}
}
