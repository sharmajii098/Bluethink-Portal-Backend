package EmployeeManagementSystem.service;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
/** Opt-in external providers. No payload logging, redirects, arbitrary endpoints or silent cloud fallback. */
@Service public class AiProviderClient {
 private final Environment env;private final ObjectMapper json=new ObjectMapper();
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 public AiProviderClient(Environment env){this.env=env;}
 @jakarta.annotation.PostConstruct public void validateConfiguration(){
  if(cloudTimeoutSeconds()<5||cloudTimeoutSeconds()>120)throw new IllegalStateException("Cloud AI timeout must be 5–120 seconds");
  if(groqTimeoutSeconds()<5||groqTimeoutSeconds()>120)throw new IllegalStateException("Groq AI timeout must be 5–120 seconds");
  if(!"groq".equals(provider()))throw new IllegalStateException("AI provider must be groq");
  if(cloudApproved()&&env.getProperty("bluethink.ai.groq-key","").isBlank())throw new IllegalStateException("Approved Groq inference requires credentials");
  if(env.getProperty("bluethink.ai.knowledge-enabled",Boolean.class,false)&&(!cloudApproved()||env.getProperty("bluethink.ai.jina-key","").isBlank()))throw new IllegalStateException("Knowledge search requires external-data approval and Jina credentials");
 }
 private int cloudTimeoutSeconds(){return env.getProperty("bluethink.ai.cloud-timeout-seconds",Integer.class,35);}
 private int groqTimeoutSeconds(){return env.getProperty("bluethink.ai.groq-timeout-seconds",Integer.class,cloudTimeoutSeconds());}
 // Groq interactive requests can use a shorter deadline without affecting Jina indexing.
 int requestTimeoutSeconds(String endpoint){
  return "https://api.groq.com/openai/v1/chat/completions".equals(endpoint)?groqTimeoutSeconds():cloudTimeoutSeconds();
 }
 public String provider(){return env.getProperty("bluethink.ai.provider","groq").toLowerCase(Locale.ROOT);}
 public boolean cloudApproved(){return env.getProperty("bluethink.ai.external-data-approved",Boolean.class,false);}
 public boolean knowledgeEnabled(){return env.getProperty("bluethink.ai.knowledge-enabled",Boolean.class,false)&&cloudApproved();}
 public String embeddingModel(){return env.getProperty("bluethink.ai.jina-model","jina-embeddings-v3");}
 public String complete(String system,String question){
  // Never attempt local inference or another cloud provider when consent/credentials are absent.
  if(!"groq".equals(provider())||!cloudApproved())throw unavailable("Groq AI requires explicit company external-data approval");
  if(env.getProperty("bluethink.ai.groq-key","").isBlank())
   throw unavailable("Groq AI credentials are not configured");
  return completeGroq(system,question);
 }
 private String completeGroq(String system,String question){
  JsonNode result=post("https://api.groq.com/openai/v1/chat/completions",env.getProperty("bluethink.ai.groq-key",""),Map.of("model",env.getProperty("bluethink.ai.groq-model","llama-3.3-70b-versatile"),"messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",question)),"temperature",0.1,"max_completion_tokens",Math.max(64, Math.min(1024, env.getProperty("bluethink.ai.max-output-tokens",Integer.class,360)))));
  String answer=result.path("choices").path(0).path("message").path("content").asText("");if(answer.isBlank())throw unavailable("AI returned an empty response");return answer;
 }
 public List<double[]> embed(List<String> texts,boolean query){
  if(!knowledgeEnabled())throw unavailable("Company knowledge is not enabled and approved");
  if(texts.isEmpty()||texts.size()>64)throw new IllegalArgumentException("Embedding batch must contain 1–64 passages");
  JsonNode result=post("https://api.jina.ai/v1/embeddings",env.getProperty("bluethink.ai.jina-key",""),Map.of("model",embeddingModel(),"input",texts,"task",query?"retrieval.query":"retrieval.passage","dimensions",512,"embedding_type","float"));
  List<double[]> vectors=new ArrayList<>(Collections.nCopies(texts.size(),null));
  for(JsonNode row:result.path("data")){int index=row.path("index").asInt(-1);JsonNode values=row.path("embedding");if(index<0||index>=texts.size()||vectors.get(index)!=null||values.size()!=512)throw unavailable("Invalid embedding response");double[] vector=new double[512];double norm=0;for(int i=0;i<512;i++){if(!values.get(i).isNumber())throw unavailable("Invalid embedding number");vector[i]=values.get(i).asDouble();if(!Double.isFinite(vector[i]))throw unavailable("Invalid embedding number");norm+=vector[i]*vector[i];}if(norm==0||!Double.isFinite(norm))throw unavailable("Invalid embedding norm");vectors.set(index,vector);}
  if(vectors.stream().anyMatch(Objects::isNull))throw unavailable("Incomplete embedding response");return vectors;
 }
 protected JsonNode post(String endpoint,String key,Object body){
  if(key.isBlank())throw unavailable("AI provider credentials are not configured");
  try{
   byte[] payload=json.writeValueAsBytes(body);
   int timeoutSeconds=requestTimeoutSeconds(endpoint);
   var req=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(timeoutSeconds)).header("Authorization","Bearer "+key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
   for(int attempt=0;attempt<1;attempt++){
    var pending=client.sendAsync(req, info -> new BoundedBody());
    HttpResponse<byte[]> response;
    try { response=pending.get(timeoutSeconds,TimeUnit.SECONDS); }
    catch(TimeoutException|InterruptedException e){pending.cancel(true);throw e;}
    {
     // Fail fast on overload. The user can retry; do not double the interactive deadline.
     if(response.statusCode()!=200)throw unavailable(response.statusCode()==429?"AI provider rate limit reached":"AI provider request failed");
     return json.readTree(response.body());
    }
   }
   throw unavailable("AI provider unavailable");
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable("AI request interrupted");}catch(IOException|IllegalArgumentException|ExecutionException|TimeoutException e){throw unavailable("AI provider connection or response failed");}
 }
 /** Cancels oversized bodies before accumulating more than 2 MB. */
 static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
  private final CompletableFuture<byte[]> result=new CompletableFuture<>();
  private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  private Flow.Subscription subscription;
  public CompletionStage<byte[]> getBody(){return result;}
  public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
  public void onNext(List<ByteBuffer> buffers){
   for(ByteBuffer b:buffers){
    if(b.remaining()>2_000_000-bytes.size()){subscription.cancel();result.completeExceptionally(new IOException("AI response exceeded the limit"));return;}
    byte[] part=new byte[b.remaining()];b.get(part);bytes.writeBytes(part);
   }
   subscription.request(1);
  }
  public void onError(Throwable error){result.completeExceptionally(error);}
  public void onComplete(){result.complete(bytes.toByteArray());}
 }
 private AiModelUnavailableException unavailable(String message){return new AiModelUnavailableException(message,null);}
}
