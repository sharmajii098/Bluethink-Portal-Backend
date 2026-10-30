package EmployeeManagementSystem.service;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class CalendarRedirectPolicyTest {
 @Test void exactProviderCallbackIsRequired(){
  assertTrue(CalendarRedirectPolicy.valid("https://portal.example.invalid/calendar/oauth/callback/google","google"));
  assertFalse(CalendarRedirectPolicy.valid("https://portal.example.invalid/calendar/oauth/callback/microsoft","google"));
  assertFalse(CalendarRedirectPolicy.valid("https://portal.example.invalid/other","google"));
  assertFalse(CalendarRedirectPolicy.valid("http://portal.example.invalid/calendar/oauth/callback/google","google"));
 }
 @Test void readinessIsFalseForWrongCallbackWithoutBreakingStatusList(){
  var env=new MockEnvironment().withProperty("bluethink.calendar.google.enabled","true")
   .withProperty("bluethink.calendar.google.client-id","client")
   .withProperty("bluethink.calendar.google.client-secret","secret")
   .withProperty("bluethink.calendar.google.redirect-uri","https://portal.example.invalid/wrong")
   .withProperty("bluethink.calendar.encryption-key",java.util.Base64.getEncoder().encodeToString(new byte[32]));
  var settings=new CalendarSettings(env);
  assertFalse(settings.configured("google"));
  assertThrows(IllegalStateException.class,()->settings.provider("google"));
 }
 @Test void callbackMustMatchTheFrontendOrigin(){
  assertTrue(CalendarRedirectPolicy.samePortalOrigin("https://portal.example.invalid/calendar/oauth/callback/google","https://portal.example.invalid"));
  assertFalse(CalendarRedirectPolicy.samePortalOrigin("https://bad.example.invalid/calendar/oauth/callback/google","https://portal.example.invalid"));
  assertFalse(CalendarRedirectPolicy.samePortalOrigin("https://portal.example.invalid:8443/calendar/oauth/callback/google","https://portal.example.invalid"));
  assertFalse(CalendarRedirectPolicy.samePortalOrigin("https://portal.example.invalid/calendar/oauth/callback/google","https://portal.example.invalid/subpath"));
 }
 @Test void malformedMicrosoftTenantIsRejected(){
  assertTrue(CalendarTenantPolicy.valid("organizations"));
  assertTrue(CalendarTenantPolicy.valid("25b82d36-8735-4ba8-9191-217a186bdc36"));
  assertFalse(CalendarTenantPolicy.valid("-".repeat(36)));
  assertFalse(CalendarTenantPolicy.valid("1-1-1-1-1"));
 }
}
