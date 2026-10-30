package EmployeeManagementSystem.service;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AiKnowledgeRulesTest {
 @Test void explicitKnowledgeIntentOnly(){assertTrue(AiKnowledgeService.handles("Search company knowledge: leave"));assertFalse(AiKnowledgeService.handles("show my payroll"));assertFalse(AiKnowledgeService.handles(null));}
 @Test void chunksCoverDocumentWithOverlap(){String text="x".repeat(2600);var chunks=AiKnowledgeService.chunks(text);assertEquals(3,chunks.size());assertEquals(1200,chunks.get(0).length());assertEquals(500,chunks.get(2).length());assertTrue(AiKnowledgeService.chunks(" ").isEmpty());}
 @Test void oversizedCorpusRejected(){assertThrows(ResponseStatusException.class,()->AiKnowledgeService.chunks("x".repeat(60001)));}
 @Test void vectorShapeAndInvalidNumbersFailClosed(){assertEquals(1,AiKnowledgeService.cosine(new double[]{1,0},new double[]{2,0}),.00001);assertEquals(-1,AiKnowledgeService.cosine(new double[]{1},new double[]{1,2}));assertEquals(-1,AiKnowledgeService.cosine(new double[]{0},new double[]{0}));assertEquals(-1,AiKnowledgeService.cosine(new double[]{Double.NaN},new double[]{1}));}
 @Test void cloudRequiresExplicitApprovalAndKeys(){var env=new MockEnvironment().withProperty("bluethink.ai.provider","groq");var client=new AiProviderClient(env);assertDoesNotThrow(client::validateConfiguration);
 assertThrows(AiModelUnavailableException.class,()->client.complete("system","question"));
 env.withProperty("bluethink.ai.external-data-approved","true");
 assertThrows(IllegalStateException.class,client::validateConfiguration);
 env.withProperty("bluethink.ai.groq-key","test-key");
 assertDoesNotThrow(client::validateConfiguration);}
 @Test void noExternalCallsWhenKnowledgeDisabled(){var client=new AiProviderClient(new MockEnvironment());assertFalse(client.knowledgeEnabled());assertThrows(AiModelUnavailableException.class,()->client.embed(List.of("private"),true));}
}
