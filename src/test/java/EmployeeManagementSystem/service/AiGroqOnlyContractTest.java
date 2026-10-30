package EmployeeManagementSystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic tests: never contact Groq, Jina or a private database. */
class AiGroqOnlyContractTest {
    static class StubProvider extends AiProviderClient {
        int requests;
        String endpoint;
        String authKey;
        StubProvider(MockEnvironment env) { super(env); }
        @Override protected JsonNode post(String url, String key, Object body) {
            requests++;
            endpoint = url;
            authKey = key;
            var response = new ObjectMapper().createObjectNode();
            response.putArray("choices").addObject().putObject("message").put("content", "Synthetic model response");
            return response;
        }
    }

    @Test void unapprovedInferenceNeverCallsExternalProvider() {
        var client = new StubProvider(new MockEnvironment());
        assertDoesNotThrow(client::validateConfiguration);
        assertThrows(AiModelUnavailableException.class,
                () -> client.complete("General HR guidance", "What is a unit test?"));
        assertEquals(0, client.requests);
    }

    @Test void legacyProviderValueIsRejected() {
        for (String legacy : new String[] {"ollama", "hybrid", "unknown"}) {
            var client = new StubProvider(new MockEnvironment().withProperty("bluethink.ai.provider", legacy));
            assertThrows(IllegalStateException.class, client::validateConfiguration);
        }
    }

    @Test void approvedInferenceRequiresCredentials() {
        var client = new StubProvider(new MockEnvironment()
                .withProperty("bluethink.ai.external-data-approved", "true"));
        assertThrows(IllegalStateException.class, client::validateConfiguration);
        assertThrows(AiModelUnavailableException.class,
                () -> client.complete("system", "question"));
        assertEquals(0, client.requests);
    }

    @Test void approvedGroqUsesOnlyPinnedEndpointAndReturnsContent() {
        var client = new StubProvider(new MockEnvironment()
                .withProperty("bluethink.ai.provider", "groq")
                .withProperty("bluethink.ai.external-data-approved", "true")
                .withProperty("bluethink.ai.groq-key", "synthetic-test-key"));
        assertDoesNotThrow(client::validateConfiguration);
        assertEquals("Synthetic model response", client.complete("system", "question"));
        assertEquals("https://api.groq.com/openai/v1/chat/completions", client.endpoint);
        assertEquals("synthetic-test-key", client.authKey);
        assertEquals(1, client.requests);
    }

    @Test void knowledgeSearchRequiresIndependentApprovalAndJinaKey() {
        var withoutApproval = new StubProvider(new MockEnvironment()
                .withProperty("bluethink.ai.knowledge-enabled", "true"));
        assertThrows(IllegalStateException.class, withoutApproval::validateConfiguration);
        var approvedWithoutJina = new StubProvider(new MockEnvironment()
                .withProperty("bluethink.ai.knowledge-enabled", "true")
                .withProperty("bluethink.ai.external-data-approved", "true")
                .withProperty("bluethink.ai.groq-key", "synthetic"));
        assertThrows(IllegalStateException.class, approvedWithoutJina::validateConfiguration);
    }
}
