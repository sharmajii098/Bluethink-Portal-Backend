package EmployeeManagementSystem.service;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.util.*;
@Component
public class CalendarSettings {
 private final Environment env;
 public CalendarSettings(Environment env){this.env=env;}
 public record Provider(String name,String clientId,String secret,String redirectUri,String authorizeUrl,String tokenUrl,String scope){}
 public String key(){return env.getProperty("bluethink.calendar.encryption-key","");}
 public String name(String provider){String p=provider==null?"":provider.toLowerCase(Locale.ROOT);if(!Set.of("google","microsoft").contains(p))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown calendar provider");return p;}
 public boolean configured(String provider){try{provider(provider);return true;}catch(IllegalStateException|ResponseStatusException e){return false;}}
 public Provider provider(String provider){
  String p=name(provider),prefix="bluethink.calendar."+p+".";
  String id=env.getProperty(prefix+"client-id",""),secret=env.getProperty(prefix+"client-secret",""),redirect=env.getProperty(prefix+"redirect-uri","");
  if(!Boolean.parseBoolean(env.getProperty(prefix+"enabled","false"))||id.isBlank()||secret.isBlank()||redirect.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Calendar provider is not configured");
  IntegrationCrypto.validateKey(key());
  if(!CalendarRedirectPolicy.valid(redirect,p) || !CalendarRedirectPolicy.samePortalOrigin(redirect,env.getProperty("portal.frontend-url","http://localhost:5173")))throw new IllegalStateException("Calendar OAuth callback must match the configured portal origin and exact /calendar/oauth/callback/"+p+" route (HTTPS except localhost development)");
  if("google".equals(p))return new Provider(p,id,secret,redirect,"https://accounts.google.com/o/oauth2/v2/auth","https://oauth2.googleapis.com/token","https://www.googleapis.com/auth/calendar.events.owned");
  String tenant=env.getProperty(prefix+"tenant","organizations");
  if(!CalendarTenantPolicy.valid(tenant))throw new IllegalStateException("Invalid Microsoft tenant");
  String endpoint="https://login.microsoftonline.com/"+tenant+"/oauth2/v2.0/";
  return new Provider(p,id,secret,redirect,endpoint+"authorize",endpoint+"token","offline_access https://graph.microsoft.com/Calendars.ReadWrite");
 }
}
