package EmployeeManagementSystem.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import EmployeeManagementSystem.service.AiPortalIntent.Module;

class AiPortalIntentTest {
    @Test void classifiesSupportedPortalModules() {
        assertEquals(Module.SUPPORT, AiPortalIntent.detect("Show my support tickets"));
        assertEquals(Module.ASSETS, AiPortalIntent.detect("List my assigned assets"));
        assertEquals(Module.EXPENSES, AiPortalIntent.detect("What is my expense claim status?"));
        assertEquals(Module.TRAINING, AiPortalIntent.detect("Show available training courses"));
        assertEquals(Module.PERFORMANCE, AiPortalIntent.detect("Show my team's performance reviews"));
        assertEquals(Module.RECRUITMENT, AiPortalIntent.detect("Show published job openings"));
        assertEquals(Module.MEETINGS, AiPortalIntent.detect("Show upcoming shared portal meetings"));
    }
    @Test void avoidsAccidentalKeywordMatches() {
        assertEquals(Module.NONE, AiPortalIntent.detect("Show my attendance"));
        assertEquals(Module.NONE, AiPortalIntent.detect("Can you help me?"));
        assertEquals(Module.NONE, AiPortalIntent.detect("How do I handle an assessment?"));
        assertEquals(Module.NONE, AiPortalIntent.detect(null));
    }
}
