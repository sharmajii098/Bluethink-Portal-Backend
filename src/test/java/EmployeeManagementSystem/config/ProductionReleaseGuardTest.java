package EmployeeManagementSystem.config;
import EmployeeManagementSystem.service.CalendarSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class ProductionReleaseGuardTest {
 private MockEnvironment config() {
  return new MockEnvironment().withProperty("security.jwt.secret","a".repeat(64))
   .withProperty("spring.jpa.hibernate.ddl-auto","validate")
   .withProperty("hrms.bootstrap-admin.enabled","false")
   .withProperty("bluethink.chat.redis-enabled","true")
   .withProperty("chat.antivirus.required","true")
   .withProperty("spring.datasource.url","jdbc:mysql://mysql:3306/employees_db?serverTimezone=UTC")
   .withProperty("portal.frontend-url","https://localhost:8443");
 }
 @Test void acceptsConsistentProductionConfiguration(){var e=config();assertDoesNotThrow(()->new ProductionReleaseGuard(e,new CalendarSettings(e)).afterPropertiesSet());}
 @Test void rejectsWrongDatabaseAndHttp(){
  var e=config().withProperty("spring.datasource.url","jdbc:mysql://mysql:3306/wrong_database");
  assertThrows(IllegalStateException.class,()->new ProductionReleaseGuard(e,new CalendarSettings(e)).afterPropertiesSet());
  var http=config().withProperty("portal.frontend-url","http://localhost:8088");
  assertThrows(IllegalStateException.class,()->new ProductionReleaseGuard(http,new CalendarSettings(http)).afterPropertiesSet());
 }
 @Test void enabledMeetingEmailsRequireAuthenticatedStarttlsSmtp(){
  var bad=config().withProperty("bluethink.meetings.email.enabled","true");
  assertThrows(IllegalStateException.class,()->new ProductionReleaseGuard(bad,new CalendarSettings(bad)).afterPropertiesSet());
  var ready=config().withProperty("bluethink.meetings.email.enabled","true")
   .withProperty("bluethink.meetings.email.from","no-reply@example.test")
   .withProperty("spring.mail.host","smtp.example.test")
   .withProperty("spring.mail.port","587")
   .withProperty("spring.mail.username","staging-account")
   .withProperty("spring.mail.password","staging-secret")
   .withProperty("spring.mail.properties.mail.smtp.auth","true")
   .withProperty("spring.mail.properties.mail.smtp.starttls.enable","true")
   .withProperty("spring.mail.properties.mail.smtp.starttls.required","true");
  assertDoesNotThrow(()->new ProductionReleaseGuard(ready,new CalendarSettings(ready)).afterPropertiesSet());
  var insecure=ready.withProperty("spring.mail.properties.mail.smtp.starttls.required","false");
  assertThrows(IllegalStateException.class,()->new ProductionReleaseGuard(insecure,new CalendarSettings(insecure)).afterPropertiesSet());
 }
 @Test void productionAndBootstrapExcludeAutomaticDdl(){
  var annotation=AttendanceStatusSchemaInitializer.class.getAnnotation(org.springframework.context.annotation.Profile.class);
  assertArrayEquals(new String[]{"!prod & !bootstrap"},annotation.value());
 }
}
