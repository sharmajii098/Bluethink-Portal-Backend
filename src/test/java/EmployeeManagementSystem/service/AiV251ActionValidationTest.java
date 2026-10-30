package EmployeeManagementSystem.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AiV251ActionValidationTest {
    @Test void hrRequestAllowsOnlySpecifiedFields() {
        var fields = Map.of("subject", "Missing HR document", "description", "Please issue the updated verification document.",
                "category", "DOCUMENTS", "priority", "MEDIUM");
        assertEquals("DOCUMENTS", AiActionService.validate("HR_SERVICE_REQUEST", fields).get("category"));
        assertThrows(ResponseStatusException.class, () -> AiActionService.validate("HR_SERVICE_REQUEST",
                Map.of("subject", "Missing HR document", "description", "Please issue the updated verification document.",
                        "category", "DOCUMENTS", "priority", "HIGH", "userId", "SOMEONE_ELSE")));
    }
    @Test void overtimeRejectsFutureAndInvalidHours() {
        var valid = Map.of("workDate", LocalDate.now().toString(), "hours", "2.5",
                "reason", "Completed urgent after-hours work", "project", "Portal", "compensationMode", "COMP_OFF");
        assertEquals("COMP_OFF", AiActionService.validate("OVERTIME_REQUEST", valid).get("compensationMode"));
        assertThrows(ResponseStatusException.class, () -> AiActionService.validate("OVERTIME_REQUEST",
                Map.of("workDate", LocalDate.now().plusDays(1).toString(), "hours", "2",
                        "reason", "Completed urgent after-hours work", "project", "Portal", "compensationMode", "PAYMENT")));
        assertThrows(ResponseStatusException.class, () -> AiActionService.validate("OVERTIME_REQUEST",
                Map.of("workDate", LocalDate.now().toString(), "hours", "25",
                        "reason", "Completed urgent after-hours work", "project", "Portal", "compensationMode", "PAYMENT")));
    }
}
