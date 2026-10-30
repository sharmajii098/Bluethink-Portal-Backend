package EmployeeManagementSystem.entity;
import jakarta.persistence.*;
@Entity @Table(name="standup_settings")
public class StandupSettings {
 @Id private Long id=1L;
 @Column(nullable=false,length=1000) private String meetingUrl="";
 @Column(nullable=false,length=20) private String meetingTime="17:30";
 @Column(nullable=false,length=120) private String timezone="Asia/Kolkata";
 @Column(nullable=false,length=200) private String workingDays="MON,TUE,WED,THU,FRI";
 @Column(nullable=false) private boolean remindersEnabled=false;
 public Long getId(){return id;} public void setId(Long v){id=v;} public String getMeetingUrl(){return meetingUrl;} public void setMeetingUrl(String v){meetingUrl=v;}
 public String getMeetingTime(){return meetingTime;} public void setMeetingTime(String v){meetingTime=v;} public String getTimezone(){return timezone;} public void setTimezone(String v){timezone=v;}
 public String getWorkingDays(){return workingDays;} public void setWorkingDays(String v){workingDays=v;} public boolean isRemindersEnabled(){return remindersEnabled;} public void setRemindersEnabled(boolean v){remindersEnabled=v;}
}
