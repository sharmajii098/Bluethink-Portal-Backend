package EmployeeManagementSystem.service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/** Requests go only to fixed vendor endpoints. Redirects never receive bearer credentials. */
@Component
public class CalendarHttpClient {
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final ObjectMapper json=new ObjectMapper();
 private static final int MAX_RESPONSE_BYTES = 1_048_576;
 public record Result(int status,Map<String,Object> body){}
 public static final class RemoteFailure extends RuntimeException {
  public final int status;
  public RemoteFailure(int status){super("Calendar provider request failed ("+status+")");this.status=status;}
 }
 public static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
 public static String form(Map<String,String> values){return values.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(Collectors.joining("&"));}
 public Map<String,Object> token(CalendarSettings.Provider p,Map<String,String> fields){
  Map<String,String> data=new LinkedHashMap<>(fields);data.put("client_id",p.clientId());data.put("client_secret",p.secret());
  Result result=send(p.tokenUrl(),"POST",null,form(data),"application/x-www-form-urlencoded");
  if(result.status()<200||result.status()>=300)throw new RemoteFailure("invalid_grant".equals(result.body().get("error"))?401:result.status());
  return result.body();
 }
 public Result api(String url,String method,String token,Map<String,Object> body){
  try{return send(url,method,token,body==null?null:json.writeValueAsString(body),"application/json");}
  catch(java.io.IOException e){throw new RemoteFailure(502);}
 }
 private Result send(String url,String method,String token,String body,String contentType){
  URI uri=URI.create(url);
  if(!"https".equals(uri.getScheme())||uri.getUserInfo()!=null||uri.getPort()!=-1||!Set.of("oauth2.googleapis.com","www.googleapis.com","graph.microsoft.com","login.microsoftonline.com").contains(uri.getHost()))throw new IllegalArgumentException("Unapproved calendar endpoint");
  HttpRequest.Builder builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).header("Accept","application/json");
  if(token!=null)builder.header("Authorization","Bearer "+token);
  if(uri.getHost().equals("graph.microsoft.com"))builder.header("Prefer","IdType=\"ImmutableId\"");
  if(body!=null)builder.header("Content-Type",contentType);
  builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8));
  try {
   // Limit bytes while streaming. A post-download String.length() check allows an untrusted
   // calendar endpoint to buffer an arbitrarily large response before rejecting it.
   HttpResponse<InputStream> response=http.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());
   int status=response.statusCode();
   byte[] raw;
   try(InputStream stream=response.body()) {
    raw=stream.readNBytes(MAX_RESPONSE_BYTES+1);
   }
   if(raw.length>MAX_RESPONSE_BYTES)throw new RemoteFailure(502);
   String value=new String(raw,StandardCharsets.UTF_8);
   if(value.isBlank())return new Result(status,Map.of());
   try {
    Map<String,Object> data=json.readValue(value,new TypeReference<Map<String,Object>>(){});
    return new Result(status,data==null?Map.of():data);
   }catch(IOException invalidJson){
    // Error responses can be HTML (e.g. a vendor proxy's 404/429 page). Preserve the
    // actual HTTP status, so deletion can correctly treat 404 as already removed.
    if(status<200||status>=300)return new Result(status,Map.of());
    throw new RemoteFailure(502);
   }
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new RemoteFailure(503);}
  catch(java.io.IOException|IllegalArgumentException e){throw new RemoteFailure(502);}
 }
}
