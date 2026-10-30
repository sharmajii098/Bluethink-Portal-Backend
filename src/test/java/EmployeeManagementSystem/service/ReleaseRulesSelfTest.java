package EmployeeManagementSystem.service;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
/** Dependency-free assertions against the same production helper classes, not duplicate implementations. */
public final class ReleaseRulesSelfTest {
 static int passed;
 static void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);passed++;}
 static void rejects(Runnable action,String message){try{action.run();}catch(IllegalArgumentException|IllegalStateException ex){passed++;return;}throw new AssertionError(message);}
 static PayrollReconciliationRules.Policy policy(){return new PayrollReconciliationRules.Policy("POLICY-TEST",Set.of(DayOfWeek.MONDAY,DayOfWeek.TUESDAY,DayOfWeek.WEDNESDAY,DayOfWeek.THURSDAY,DayOfWeek.FRIDAY),480,240,true,1,Map.of("CASUAL","PAID","LOSS_OF_PAY","UNPAID"));}
 static PayrollReconciliationRules.Day day(String status,Integer minutes,boolean open,int count,boolean approved,double paid,double unpaid,List<String> issues,PayrollReconciliationRules.Decision decision){return PayrollReconciliationRules.day(new PayrollReconciliationRules.Input(LocalDate.of(2026,10,1),true,true,480,status,minutes,open,count,approved,paid,unpaid,0,issues,decision),policy());}
 public static void main(String[] args){
  String key=Base64.getEncoder().encodeToString(new byte[32]);String context="EMP_TEST:google";
  String encrypted=IntegrationCrypto.encrypt("test refresh token",key,context);
  ok("test refresh token".equals(IntegrationCrypto.decrypt(encrypted,key,context)),"AES-GCM roundtrip");
  ok(!encrypted.contains("test refresh token"),"plaintext is not stored");
  ok(!encrypted.equals(IntegrationCrypto.encrypt("test refresh token",key,context)),"random nonce per encryption");
  rejects(()->IntegrationCrypto.decrypt(encrypted,key,"EMP_OTHER:google"),"owner-bound token");
  rejects(()->IntegrationCrypto.decrypt(encrypted,key,"EMP_TEST:microsoft"),"provider-bound token");
  String[] parts=encrypted.split("\\.");byte[] cipher=Base64.getDecoder().decode(parts[2]);cipher[0]^=1;
  String tampered=parts[0]+"."+parts[1]+"."+Base64.getEncoder().encodeToString(cipher);
  rejects(()->IntegrationCrypto.decrypt(tampered,key,context),"tamper detection");
  rejects(()->IntegrationCrypto.validateKey("abc"),"wrong key rejected");
  rejects(()->IntegrationCrypto.decrypt("v0.bad.bad",key,context),"unsupported token format");
  ok(IntegrationCrypto.pkce("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk").equals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"),"RFC7636 PKCE vector");
  ok(IntegrationCrypto.randomToken().matches("[A-Za-z0-9_-]{43}"),"opaque token format");
  ok(IntegrationCrypto.sha256("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),"SHA256 vector");
  String summary="SUMMARY:"+"\u0939\u093f\u0902\u0926\u0940\ud83d\ude00".repeat(30);String folded=MeetingCalendarText.fold(summary);
  ok(Arrays.stream(folded.split("\r\n")).allMatch(line->line.getBytes(StandardCharsets.UTF_8).length<=75),"ICS octet limit");
  ok(folded.replace("\r\n ","").replace("\r\n","").equals(summary),"fold/unfold preserves Unicode");
  ok(MeetingCalendarText.escape("a,b;c\nx\\y").equals("a\\,b\\;c\\nx\\\\y"),"ICS escaping");
  OffsetDateTime start=OffsetDateTime.parse("2026-10-19T10:00:00+05:30"),end=start.plusMinutes(30);
  String ics=MeetingCalendarText.build(9,"Test\nEND:VEVENT","Agenda","https://meet.google.com/test",start,end,2,true,"hr@example.invalid","test@example.invalid");
  ok(ics.contains("DTSTART:20261019T043000Z\r\n"),"ICS UTC date");
  ok(ics.contains("METHOD:CANCEL\r\n")&&ics.contains("SEQUENCE:2\r\n"),"cancellation and sequence");
  ok(ics.contains("SUMMARY:Test\\nEND:VEVENT"),"newline injection escaped");
  ok(ics.contains("UID:bluethink-meeting-9@portal"),"stable calendar UID");
  ok(MeetingCalendarText.build(9,"Test","","https://meet.google.com/test",start,end,0,false,null,null).contains("METHOD:PUBLISH"),"publish does not invite attendees");
  for(String bad:List.of("http://meet.google.com/x","https://meet.google.com.evil.invalid/x","https://zoom.us@evil.invalid/x","https://zoom.us:8443/x","javascript:alert(1)","https://zoom.us/x\r\nBad: yes"))rejects(()->MeetingTimeRules.safeUrl(bad),"unapproved meeting URL rejected");
  ok(MeetingTimeRules.safeUrl("https://meet.google.com/abc-defg-hij").startsWith("https:"),"Google meeting URL");
  ok(MeetingTimeRules.safeUrl("https://company.zoom.us/j/123").contains("company.zoom.us"),"Zoom tenant URL");
  rejects(()->MeetingTimeRules.zone("Not/AZone"),"invalid timezone");
  OffsetDateTime before=OffsetDateTime.parse("2026-03-07T10:00:00-05:00");
  var after=MeetingTimeRules.occurrence(before,before.plusHours(1),"DAILY",1,"America/New_York");
  ok(after.startsAt().getHour()==10&&after.startsAt().getOffset().equals(ZoneOffset.ofHours(-4)),"DST preserves wall-clock hour");
  ok(Duration.between(after.startsAt(),after.endsAt()).toMinutes()==60,"recurrence duration unchanged");
  var jan=OffsetDateTime.parse("2026-01-31T10:00:00+05:30");
  ok(MeetingTimeRules.occurrence(jan,jan.plusHours(1),"MONTHLY",1,"Asia/Kolkata").startsAt().getDayOfMonth()==28,"month end is clamped");
  ok(MeetingTimeRules.occurrence(jan,jan.plusHours(1),"MONTHLY",2,"Asia/Kolkata").startsAt().getDayOfMonth()==31,"later months use original anchor");
  rejects(()->MeetingTimeRules.occurrence(start,end,"DAILY",52,"UTC"),"series limit");
  rejects(()->MeetingTimeRules.occurrence(end,start,"DAILY",1,"UTC"),"negative meeting duration");
  rejects(()->MeetingTimeRules.occurrence(start,end,"YEARLY",1,"UTC"),"unsupported recurrence");
  ok(new ArrayList<>(policy().workingDays()).get(0)==DayOfWeek.MONDAY,"policy order deterministic across processes");
  rejects(()->new PayrollReconciliationRules.Policy("",Set.of(DayOfWeek.MONDAY),480,240,true,1,Map.of()),"policy reference required");
  rejects(()->new PayrollReconciliationRules.Policy("P",Set.of(),480,240,true,1,Map.of()),"working days required");
  rejects(()->new PayrollReconciliationRules.Policy("P",Set.of(DayOfWeek.MONDAY),480,500,true,1,Map.of()),"invalid thresholds");
  ok(day("PRESENT",480,false,1,true,0,0,List.of(),null).payable()==1,"full attendance payable");
  ok(day("PRESENT",240,false,1,true,0,0,List.of(),null).payable()==0.5,"half day threshold");
  ok(day("HALF_DAY",600,false,1,true,0,0,List.of(),null).payable()==0.5,"half-day status limits pay");
  ok(day("PRESENT",480,false,1,false,0,0,List.of(),null).issues().contains("APPROVED_TIMESHEET_MISSING"),"unapproved timesheet blocked");
  ok(day("UNVERIFIED",480,false,1,true,0,0,List.of(),null).issues().contains("UNVERIFIED_ATTENDANCE"),"unverified punch blocked");
  ok(day("PRESENT",480,true,1,true,0,0,List.of(),null).issues().contains("OPEN_PUNCH"),"open session blocked");
  ok(day("PRESENT",480,false,2,true,0,0,List.of(),null).issues().contains("DUPLICATE_ATTENDANCE"),"duplicate attendance blocked");
  ok(day("ABSENT",null,false,0,false,1,0,List.of(),null).payable()==1,"approved paid leave");
  ok(day("ABSENT",null,false,0,false,0,1,List.of(),null).payable()==0,"unpaid leave not paid");
  ok(day("PRESENT",240,false,1,true,0.5,0,List.of(),null).payable()==1,"half leave and half work");
  ok(day("ABSENT",0,false,0,false,1,1,List.of(),null).issues().contains("OVERLAPPING_LEAVE"),"overlap blocked");
  ok(day("PRESENT",1500,false,1,true,0,0,List.of(),null).issues().contains("INVALID_WORK_DURATION"),"long duration blocked");
  var corrected=day("UNVERIFIED",0,false,1,false,0,0,List.of("DUPLICATE_SHIFT_ASSIGNMENTS"),new PayrollReconciliationRules.Decision(1,"Reviewed against approved punch correction"));
  ok(corrected.payable()==1&&corrected.issues().isEmpty(),"override applies");
  ok(corrected.resolvedIssues().contains("UNVERIFIED_ATTENDANCE")&&corrected.resolvedIssues().contains("DUPLICATE_SHIFT_ASSIGNMENTS"),"override retains evidence");
  rejects(()->new PayrollReconciliationRules.Decision(0.75,"reviewed by HR"),"fraction limit");
  rejects(()->new PayrollReconciliationRules.Decision(1,"short"),"override reason required");
  var outside=PayrollReconciliationRules.day(new PayrollReconciliationRules.Input(LocalDate.of(2026,10,1),true,false,480,null,null,false,0,false,0,0,0,List.of(),null),policy());
  var full=day("PRESENT",480,false,1,true,0,0,List.of(),null);
  var totals=PayrollReconciliationRules.totals(List.of(outside,full),new BigDecimal("1000.00"),new BigDecimal("50.00"),new BigDecimal("2"),new BigDecimal("100.00"));
  ok(totals.netPay().equals(new BigDecimal("650.00")),"joiner proration plus approved overtime minus deductions");
  ok(totals.lossOfPay().equals(new BigDecimal("500.00"))&&totals.nonEmploymentDays()==1,"proration audit");
  rejects(()->PayrollReconciliationRules.totals(List.of(full),BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO),"negative net rejected");
  rejects(()->PayrollReconciliationRules.totals(List.of(full),null,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO),"null amount rejected");
  rejects(()->PayrollReconciliationRules.totals(List.of(),BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO),"zero denominator rejected");
  for(var template:HrLetterStarters.all().entrySet()){LetterTemplateRules.validate(template.getKey(),template.getValue());passed++;}
  rejects(()->LetterTemplateRules.approvable(HrLetterStarters.all().get("OFFER_LETTER")),"unapproved company clauses blocked");
  rejects(()->LetterTemplateRules.validate("EMPLOYMENT_CERTIFICATE","Dear {{NAME}}"),"missing verified fields");
  rejects(()->LetterTemplateRules.validate(null,"Body"),"null letter type rejected");
  rejects(()->LetterTemplateRules.validate("EMPLOYMENT_CERTIFICATE",HrLetterStarters.all().get("EMPLOYMENT_CERTIFICATE")+"{{PASSWORD}}"),"unknown placeholder rejected");
  rejects(()->LetterTemplateRules.render("{{NAME}}",Map.of()),"missing render value rejected");
  ok(LetterTemplateRules.render("Dear {{NAME}}",Map.of("NAME","Example $1 \\ name")).equals("Dear Example $1 \\ name"),"literal replacement no regex injection");
  ok(HrLetterStarters.all().get("OFFER_LETTER").chars().filter(c->c=='\f').count()==5,"six-section offer starter");
  String reviewedOffer=HrLetterStarters.all().get("OFFER_LETTER");
  ok(reviewedOffer.contains("ANNUAL LEAVE ENTITLEMENT")&&reviewedOffer.contains("TERMINATION OF EMPLOYMENT")&&reviewedOffer.contains("APPENDIX A")&&reviewedOffer.contains("DOCUMENTS TO PRODUCE ON JOINING"),"reference-aligned six-page offer structure");
  ok(!reviewedOffer.contains("Nitesh Kumar Sharma")&&!reviewedOffer.contains("Pratiksha Kumari")&&!reviewedOffer.contains("120,000"),"source private names, signatories and compensation are not copied");
  rejects(()->LetterTemplateRules.approvable(reviewedOffer),"unverified reference clauses cannot be approved");

  System.out.println("PASS: "+passed+" release crypto/calendar/payroll/template assertions");
 }
}
