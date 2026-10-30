package EmployeeManagementSystem.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class AiProviderSpeedSettingsTest {
 @Test void separateGroqDeadlineDoesNotShortenJinaIndexing(){
  var env=new MockEnvironment().withProperty("bluethink.ai.groq-timeout-seconds","12");
  var client=new AiProviderClient(env);
  assertEquals(12,client.requestTimeoutSeconds("https://api.groq.com/openai/v1/chat/completions"));
  assertEquals(35,client.requestTimeoutSeconds("https://api.jina.ai/v1/embeddings"));
  assertDoesNotThrow(client::validateConfiguration);
 }
 @Test void defaultKeepsOldTimeouts(){
  var client=new AiProviderClient(new MockEnvironment());
  assertEquals(35,client.requestTimeoutSeconds("https://api.groq.com/openai/v1/chat/completions"));
  assertEquals(35,client.requestTimeoutSeconds("https://api.jina.ai/v1/embeddings"));
 }
 @Test void invalidSettingsRejected(){
  var client=new AiProviderClient(new MockEnvironment().withProperty("bluethink.ai.groq-timeout-seconds","1"));
  assertThrows(IllegalStateException.class,client::validateConfiguration);
 }
 @Test void overrideDoesNotChangeSharedTimeout(){
  var env=new MockEnvironment().withProperty("bluethink.ai.cloud-timeout-seconds","75")
    .withProperty("bluethink.ai.groq-timeout-seconds","12");
  var client=new AiProviderClient(env);
  assertEquals(12,client.requestTimeoutSeconds("https://api.groq.com/openai/v1/chat/completions"));
  assertEquals(75,client.requestTimeoutSeconds("https://api.jina.ai/v1/embeddings"));
  assertDoesNotThrow(client::validateConfiguration);
 }
}
