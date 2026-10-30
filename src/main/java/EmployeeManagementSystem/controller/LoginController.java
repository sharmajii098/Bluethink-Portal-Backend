package EmployeeManagementSystem.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
/** Old unaudited login path is deliberately retired. */
@RestController @RequestMapping("/auth")
public class LoginController {
 @RequestMapping("/**") public ResponseEntity<?> retired(){return ResponseEntity.status(410).body(Map.of("message","This endpoint is retired. Use the portal sign-in page."));}
}
