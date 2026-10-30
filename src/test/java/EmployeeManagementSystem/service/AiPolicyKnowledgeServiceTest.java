package EmployeeManagementSystem.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiPolicyKnowledgeServiceTest {
    private final PolicyService repository = mock(PolicyService.class);
    private final AiPolicyKnowledgeService assistant = new AiPolicyKnowledgeService(repository);

    @Test void citesMatchingEffectiveRecordsAndExcludesFuturePolicies() {
        when(repository.listForUser("EMP0042")).thenReturn(List.of(
                Map.of("id", 11L, "title", "Work From Home", "category", "Flexible Work",
                        "description", "Employees may request work from home via their manager.",
                        "effectiveDate", LocalDate.now().minusDays(10)),
                Map.of("id", 12L, "title", "Future Work From Home", "category", "Flexible Work",
                        "description", "Unpublished draft policy.",
                        "effectiveDate", LocalDate.now().plusMonths(1))));
        String result = assistant.answer("What is the work from home policy?", "EMP0042");
        assertTrue(result.contains("Policy #11"));
        assertTrue(result.contains("Employees may request work from home"));
        assertFalse(result.contains("Policy #12"));
        verify(repository).listForUser("EMP0042");
    }

    @Test void doesNotInventMissingRulesOrQueryAnUnverifiedAccount() {
        when(repository.listForUser("EMP0042")).thenReturn(List.of(
                Map.of("id", 1L, "title", "Leave", "category", "HR",
                        "description", "Leave applications are filed in the portal.")));
        assertTrue(assistant.answer("Travel allowance policy", "EMP0042").contains("could not find"));
        assertThrows(AccessDeniedException.class, () -> assistant.answer("Show policies", ""));
    }
}
