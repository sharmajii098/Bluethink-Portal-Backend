package EmployeeManagementSystem.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiKnowledgeAnswerGuardTest {
    @Test void acceptsOnlyReferencesToAuthorizedRetrievedPassages() {
        assertEquals("Policy allows this [S1] and that [S2].",
                AiKnowledgeAnswerGuard.verifiedOrFallback("Policy allows this [S1] and that [S2].", 2, "policy"));
        assertTrue(AiKnowledgeAnswerGuard.verifiedOrFallback("Invented policy [S4]", 2, "policy").contains("could not be verified"));
        assertTrue(AiKnowledgeAnswerGuard.verifiedOrFallback("Policy without references", 2, "policy").contains("could not be verified"));
        assertTrue(AiKnowledgeAnswerGuard.verifiedOrFallback("Invalid [S0]", 2, "policy").contains("could not be verified"));
        assertTrue(AiKnowledgeAnswerGuard.verifiedOrFallback("Supported [S1]. Uncited extra claim.", 2, "policy").contains("could not be verified"));
        assertTrue(AiKnowledgeAnswerGuard.verifiedOrFallback("Claim [S1]\nAnother claim without citation", 2, "policy").contains("could not be verified"));
        assertTrue(AiKnowledgeAnswerGuard.verifiedOrFallback("Valid [S1] but invented [S9999999999999999999]", 2, "policy").contains("could not be verified"));
        assertTrue(AiKnowledgeAnswerGuard.verifiedOrFallback("  ", 2, "policy").contains("could not be verified"));
        assertTrue(AiKnowledgeAnswerGuard.verifiedOrFallback(null, 2, "policy").contains("could not be verified"));
        assertTrue(AiKnowledgeAnswerGuard.verifiedOrFallback("[S1]", 0, "policy").contains("could not be verified"));
    }

    @Test void failureKeepsQuestionLanguageAndDoesNotInventLeaveEntitlements() {
        String failure = AiKnowledgeAnswerGuard.verifiedOrFallback("No references given", 2, "Mujhe leave kitni milegi?");
        assertTrue(failure.contains("verify nahi"));
        assertFalse(failure.contains("days"));
    }
}
