package EmployeeManagementSystem.deployment;
import EmployeeManagementSystem.EmployeeManagementSystemApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
/** Real HTTP + full application context on disposable H2, not MySQL certification. */
@SpringBootTest(classes=EmployeeManagementSystemApplication.class,
 webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
 "spring.datasource.url=jdbc:h2:mem:portalstartup;MODE=MySQL;NON_KEYWORDS=YEAR,MONTH;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
 "security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
 "hrms.bootstrap-admin.enabled=false", "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false",
 "bluethink.chat.redis-enabled=false", "bluethink.ai.provider=groq"
})
class ApplicationStartupTest {
 @LocalServerPort int port;
 private HttpResponse<String> get(String path) throws Exception {
  return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void startsAndProtectsPrivateRoutes() throws Exception {
  assertEquals(200,get("/api/health/ready").statusCode());
  assertEquals(401,get("/api/auth/me").statusCode());
  assertEquals(401,get("/api/employees").statusCode());
  assertEquals(401,get("/api/dashboard/stats").statusCode());
 }
 @org.springframework.beans.factory.annotation.Autowired EmployeeManagementSystem.repository.RegisterEmployeeRepository accounts;
 @org.springframework.beans.factory.annotation.Autowired EmployeeManagementSystem.repository.OfficeLocationRepository offices;
 @org.springframework.beans.factory.annotation.Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
 private HttpResponse<String> send(String path, String method, String token, String body) throws Exception {
  var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(10));
  if(token!=null)request.header("Authorization","Bearer "+token);
  request.header("Content-Type","application/json");
  request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
  return HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void fourRolesCanLoginAndLogoutWithRealJwtAndDatabaseSessions() throws Exception {
  offices.save(EmployeeManagementSystem.entity.OfficeLocation.builder().officeName("Main Office").latitude(28.61).longitude(77.21).allowedRadius(200.0).build());
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  for(String role:java.util.List.of("ADMIN","HR","MANAGER","EMPLOYEE")) {
   String id="INTEGRATION_"+role;
   var account=new EmployeeManagementSystem.entity.RegisterEmployee();account.setUserId(id);account.setName("Test "+role);account.setRole(role);
   account.setEmail(id.toLowerCase()+"@example.invalid");account.setPassword(passwords.encode("SyntheticPassword123"));accounts.save(account);
   String body="{\"userId\":\""+id+"\",\"password\":\"SyntheticPassword123\",\"latitude\":28.61,\"longitude\":77.21,\"accuracy\":20}";
   var response=send("/api/auth/login","POST",null,body);
   assertEquals(200,response.statusCode(),"Login status for "+role);
   String token=mapper.readTree(response.body()).path("token").asText();assertFalse(token.isBlank());
   assertEquals(200,send("/api/auth/me","GET",token,null).statusCode());
   assertEquals(role.equals("ADMIN")||role.equals("HR")?200:403,send("/api/employees","GET",token,null).statusCode());
   assertEquals(200,send("/api/auth/logout","POST",token,null).statusCode());
   assertEquals(401,send("/api/auth/me","GET",token,null).statusCode());
  }
 }

}
