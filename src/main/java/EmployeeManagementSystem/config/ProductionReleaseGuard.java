package EmployeeManagementSystem.config;
import EmployeeManagementSystem.service.CalendarSettings;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
/** Reject known-insecure production configuration rather than silently degrading. */
@Component @Profile("prod")
public class ProductionReleaseGuard implements InitializingBean {
 private final Environment env;private final CalendarSettings calendar;
 public ProductionReleaseGuard(Environment env,CalendarSettings calendar){this.env=env;this.calendar=calendar;}
 private String value(String key){return env.getProperty(key,"");}
 private void require(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
 @Override public void afterPropertiesSet(){
  String key=value("security.jwt.secret");
  require(key.length()>=48&&!key.contains("local-development")&&!key.contains("change-before"),"Production JWT secret must be independently generated (48+ characters)");
  require("validate".equals(value("spring.jpa.hibernate.ddl-auto")),"Run reviewed migrations first; production schema mode must be validate");
  require(!Boolean.parseBoolean(value("hrms.bootstrap-admin.enabled")),"Disable bootstrap administrator in production");
  require(Boolean.parseBoolean(value("bluethink.chat.redis-enabled")),"Production requires Redis-backed throttling");
  require(Boolean.parseBoolean(value("chat.antivirus.required")),"Production requires fail-closed attachment scanning");
  if(Boolean.parseBoolean(value("bluethink.meetings.email.enabled"))){
   String from=value("bluethink.meetings.email.from");
   require(!value("spring.mail.host").isBlank() && !value("spring.mail.username").isBlank()
     && !value("spring.mail.password").isBlank() && from.length()<=254
     && from.matches("^[^\\s@\\r\\n]+@[^\\s@\\r\\n]+\\.[^\\s@\\r\\n]+$")
     && "587".equals(value("spring.mail.port"))
     && "true".equalsIgnoreCase(value("spring.mail.properties.mail.smtp.auth"))
     && "true".equalsIgnoreCase(value("spring.mail.properties.mail.smtp.starttls.enable"))
     && "true".equalsIgnoreCase(value("spring.mail.properties.mail.smtp.starttls.required")),
     "Meeting email requires configured authenticated STARTTLS SMTP on port 587 and a valid sender");
  }
  try {
   var portal=java.net.URI.create(value("portal.frontend-url"));
   require("https".equals(portal.getScheme()) && portal.getHost()!=null && portal.getUserInfo()==null
     && portal.getQuery()==null && portal.getFragment()==null
     && (portal.getPath().isEmpty() || "/".equals(portal.getPath())), "Production frontend URL must be an HTTPS origin");
   String jdbc=value("spring.datasource.url");
   require(jdbc.startsWith("jdbc:mysql://"),"Production requires a MySQL JDBC URL");
   var database=java.net.URI.create(jdbc.substring(5));
   require("/employees_db".equals(database.getPath()),"Production database must be employees_db");
  } catch (IllegalArgumentException invalid) { throw new IllegalStateException("Invalid production database or portal URL",invalid); }
  for(String provider:java.util.List.of("google","microsoft"))if(Boolean.parseBoolean(value("bluethink.calendar."+provider+".enabled"))){
   CalendarSettings.Provider p=calendar.provider(provider);require(p.redirectUri().startsWith("https://"),"Production calendar callbacks require HTTPS");
  }
 }
}
