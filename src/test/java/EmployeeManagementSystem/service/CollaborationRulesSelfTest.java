package EmployeeManagementSystem.service;
import java.time.*;
public class CollaborationRulesSelfTest {
 private static int count;
 private static void check(boolean ok){count++;if(!ok)throw new AssertionError("Assertion "+count);}
 private static void reject(Runnable r){count++;try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected rejection "+count);}
 public static void main(String[] args){
  check(MeetingRules.link("https://meet.google.com/abc-defg-hij").startsWith("https:"));
  check(MeetingRules.link("").isEmpty());
  for(String url:new String[]{"javascript:alert(1)","http://meet.google.com/abc","https://user:pass@meet.google.com/x","https://127.0.0.1/x","//example.com/x","https://localhost/x"})reject(()->MeetingRules.link(url));
  reject(()->MeetingRules.zone("invalid/timezone"));
  check(MeetingRules.days("MON, FRI").size()==2);reject(()->MeetingRules.days("MON,EVIL"));reject(()->MeetingRules.days(""));
  LocalDate first=LocalDate.of(2026,10,19),last=first.plusDays(7);
  check(MeetingRules.occurs(first,first,"NONE",null));check(!MeetingRules.occurs(first,last,"NONE",null));
  check(MeetingRules.occurs(first,last,"WEEKLY",last));check(!MeetingRules.occurs(first,first.plusDays(1),"WEEKLY",last));
  check(!MeetingRules.occurs(first,LocalDate.of(2026,10,24),"WEEKDAYS",last));check(!MeetingRules.occurs(first,first.minusDays(1),"DAILY",last));check(!MeetingRules.occurs(first,last.plusDays(1),"DAILY",last));
  MeetingRules.schedule(first.atTime(10,0),first.atTime(10,30),"Asia/Kolkata","DAILY",last);count++;
  reject(()->MeetingRules.schedule(first.atTime(10,0),first.atTime(9,30),"Asia/Kolkata","NONE",null));
  reject(()->MeetingRules.schedule(first.atTime(10,0),first.atTime(10,30),"Asia/Kolkata","DAILY",null));
  reject(()->MeetingRules.schedule(first.atTime(10,0),first.atTime(10,30),"Asia/Kolkata","DAILY",first.plusDays(367)));
  reject(()->MeetingRules.schedule(LocalDateTime.of(2026,3,8,2,30),LocalDateTime.of(2026,3,8,3,30),"America/New_York","NONE",null));
  reject(()->MeetingRules.schedule(LocalDateTime.of(2026,11,1,1,15),LocalDateTime.of(2026,11,1,2,15),"America/New_York","NONE",null));
  LocalDateTime now=first.atTime(20,0);
  check(AttendanceEntryRules.validate(first,first.atTime(9,0),first.atTime(17,30),"PRESENT",now)==8.5);
  check(AttendanceEntryRules.validate(first,null,null,"ABSENT",now)==0);
  reject(()->AttendanceEntryRules.validate(first.plusDays(1),null,null,"ABSENT",now));
  reject(()->AttendanceEntryRules.validate(first,first.atTime(9,0),null,"PRESENT",now));
  reject(()->AttendanceEntryRules.validate(first,first.atTime(9,0),first.atTime(8,0),"PRESENT",now));
  reject(()->AttendanceEntryRules.validate(first,first.atTime(9,0),first.atTime(21,0),"PRESENT",now));
  reject(()->AttendanceEntryRules.validate(first,first.minusDays(1).atTime(9,0),first.atTime(8,0),"PRESENT",now));
  reject(()->AttendanceEntryRules.validate(first,first.atTime(9,0),first.atTime(17,0),"LEAVE",now));
  check(ChatFileRules.matches("txt","Hello team".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  check(!ChatFileRules.matches("txt",new byte[]{0,1,2}));
  check(!ChatFileRules.matches("txt",new byte[]{(byte)0xff}));
  check(!ChatFileRules.matches("png",new byte[]{(byte)137,80,78,71,0,0,0,0}));
  check(!ChatFileRules.matches("docx",new byte[]{80,75,3,4}));
  System.out.println("PASS: "+count+" collaboration/attendance rule assertions");
 }
}
