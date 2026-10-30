package EmployeeManagementSystem.service;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class V26ProviderAndEmailTest {
 @Test void emailContainsActualPlainAndHtmlAlternatives() throws Exception {
  var sender=mock(JavaMailSender.class);
  var mime=new MimeMessage(Session.getInstance(new Properties()));
  when(sender.createMimeMessage()).thenReturn(mime);
  var message=new SimpleMailMessage();message.setTo("test@example.invalid");message.setFrom("portal@example.invalid");message.setSubject("Account setup");message.setText("Hello <Sam>\n\nOpen https://portal.example.invalid/activate?token=test");
  EmailTemplates.send(sender,message,"Set up account","https://portal.example.invalid/activate?token=test");
  mime.saveChanges();verify(sender).send(mime);
  List<String> plain=new ArrayList<>(),html=new ArrayList<>();collect(mime,plain,html);
  assertEquals(1,plain.size());assertEquals(message.getText(),plain.get(0));assertEquals(1,html.size());assertTrue(html.get(0).contains("&lt;Sam&gt;"));assertTrue(html.get(0).contains("Set up account"));assertFalse(html.get(0).contains("<Sam>"));
 }
 private void collect(Part part,List<String> plain,List<String> html)throws Exception {
  if(part.isMimeType("text/plain"))plain.add((String)part.getContent());
  else if(part.isMimeType("text/html"))html.add((String)part.getContent());
  else if(part.getContent() instanceof Multipart multi)for(int i=0;i<multi.getCount();i++)collect(multi.getBodyPart(i),plain,html);
 }
 @Test void smtpFailurePropagatesForExistingRetryAndFailureHandling(){
  var sender=mock(JavaMailSender.class);when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));doThrow(new org.springframework.mail.MailSendException("offline")).when(sender).send(any(MimeMessage.class));var message=new SimpleMailMessage();message.setTo("test@example.invalid");message.setText("Message");assertThrows(org.springframework.mail.MailSendException.class,()->EmailTemplates.send(sender,message));
 }
 @Test void bodySubscriberRejectsOversizeBeforeCopying(){
  var body=new AiProviderClient.BoundedBody();var subscription=mock(Flow.Subscription.class);body.onSubscribe(subscription);body.onNext(List.of(ByteBuffer.wrap(new byte[2_000_001])));verify(subscription).cancel();assertThrows(CompletionException.class,()->body.getBody().toCompletableFuture().join());
 }
 @Test void bodySubscriberPreservesFragments(){
  var body=new AiProviderClient.BoundedBody();body.onSubscribe(mock(Flow.Subscription.class));body.onNext(List.of(ByteBuffer.wrap("{\"ok\":".getBytes()),ByteBuffer.wrap("true}".getBytes())));body.onComplete();assertEquals("{\"ok\":true}",new String(body.getBody().toCompletableFuture().join()));
 }
 @Test void embeddingResponseHonorsIndicesAndRejectsMalformedVectors(){
  class Fake extends AiProviderClient {
   com.fasterxml.jackson.databind.JsonNode response;
   Fake(){super(new MockEnvironment().withProperty("bluethink.ai.external-data-approved","true").withProperty("bluethink.ai.knowledge-enabled","true"));}
   protected com.fasterxml.jackson.databind.JsonNode post(String endpoint,String key,Object payload){return response;}
  }
  var fake=new Fake();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var root=mapper.createObjectNode();var rows=root.putArray("data");for(int index:new int[]{1,0}){var row=rows.addObject();row.put("index",index);var vector=row.putArray("embedding");for(int i=0;i<512;i++)vector.add(i==index?1.0:0.0);}
  fake.response=root;var vectors=fake.embed(List.of("A","B"),false);assertEquals(1.0,vectors.get(0)[0]);assertEquals(1.0,vectors.get(1)[1]);
  ((com.fasterxml.jackson.databind.node.ObjectNode)rows.get(1)).put("index",1);assertThrows(AiModelUnavailableException.class,()->fake.embed(List.of("A","B"),false));
 }
}
