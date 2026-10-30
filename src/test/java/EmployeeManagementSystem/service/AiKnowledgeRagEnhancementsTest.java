package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.AiKnowledgeChunk;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiKnowledgeRagEnhancementsTest {
    @Test void matchesHinglishButNotEveryEnglishQuestion() {
        assertTrue(AiKnowledgeReplyPolicy.hinglish("Leave policy me kitni chhutti milegi?"));
        assertTrue(AiKnowledgeReplyPolicy.hinglish("Mujhe probation leave ka rule batao"));
        assertFalse(AiKnowledgeReplyPolicy.hinglish("How many paid leave days are allowed?"));
        assertFalse(AiKnowledgeReplyPolicy.hinglish(null));
    }

    @Test void answersStayGroundedInUserLanguage() {
        assertTrue(AiKnowledgeReplyPolicy.responseInstructions("Mujhe leave kab milegi?").contains("Hinglish"));
        assertTrue(AiKnowledgeReplyPolicy.responseInstructions("Explain annual leave").contains("English"));
        assertTrue(AiKnowledgeReplyPolicy.noRelevantSources("Meri chhutti kitni hai?").contains("guess nahi"));
        assertTrue(AiKnowledgeReplyPolicy.noRelevantSources("How many days?").contains("No sufficiently relevant"));
        assertTrue(AiKnowledgeReplyPolicy.noAuthorizedSources("Mujhe leave batao").contains("role"));
    }

    @Test void pageCitationsAreOnlyForPageTaggedPdfPassages() {
        AiKnowledgeChunk pdf = new AiKnowledgeChunk();
        pdf.filename = "Leave_Policy.PDF";
        pdf.content = "[PDF page 12] Casual leave is subject to the approved policy.";
        assertEquals(", page 12", AiKnowledgePdfPages.pageCitation(pdf));
        pdf.content = "Legacy PDF passage without page metadata";
        assertEquals("", AiKnowledgePdfPages.pageCitation(pdf));
        pdf.filename = "policy.txt";
        pdf.content = "[PDF page 12] not a real PDF tag";
        assertEquals("", AiKnowledgePdfPages.pageCitation(pdf));
        assertEquals("", AiKnowledgePdfPages.pageCitation(null));
    }
}
