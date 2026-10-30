package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.enums.LeaveStatus;
import EmployeeManagementSystem.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PayrollReconciliationService {
 @org.springframework.beans.factory.annotation.Autowired private ReleaseAudit audit;

 private final PayrollReconciliationRepository records;private final EmployeeProfileRepository profiles;private final AttendanceRepository attendance;
 private final LeaveRepository leaves;private final TimesheetRepository timesheets;private final HolidayRepository holidays;private final ShiftAssignmentRepository shifts;
 private final OvertimeRequestRepository overtime;private final SalaryStructureService structures;private final EmployeeSeparationRepository separations;private final PortalActor actor;private final ZoneId zone;
 @org.springframework.beans.factory.annotation.Autowired private ApprovedPayrollPolicyService approvedPolicies;
 private final ObjectMapper json=new ObjectMapper().findAndRegisterModules().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
 public PayrollReconciliationService(PayrollReconciliationRepository records,EmployeeProfileRepository profiles,AttendanceRepository attendance,LeaveRepository leaves,
      TimesheetRepository timesheets,HolidayRepository holidays,ShiftAssignmentRepository shifts,OvertimeRequestRepository overtime,SalaryStructureService structures,
      EmployeeSeparationRepository separations,PortalActor actor,@Value("${bluethink.business-zone:Asia/Kolkata}") String zone){
  this.records=records;this.profiles=profiles;this.attendance=attendance;this.leaves=leaves;this.timesheets=timesheets;this.holidays=holidays;this.shifts=shifts;this.overtime=overtime;this.structures=structures;this.separations=separations;this.actor=actor;this.zone=ZoneId.of(zone);
 }
 public record Input(Long profileId,String period,PayrollReconciliationRules.Policy policy,Map<LocalDate,PayrollReconciliationRules.Decision> decisions,BigDecimal overtimeHourlyRate){}
 public record Report(String employeeUserId,String period,String policyReference,String fingerprint,List<PayrollReconciliationRules.Day> days,
       PayrollReconciliationRules.Totals totals,BigDecimal approvedOvertimeHours,List<String> issues){}
 public record View(Long id,Long profileId,String period,String status,String createdBy,String approvedBy,Report report,Input input){}

 public record EmployeeOption(Long profileId,String userId,String name,String status){}
 @Transactional(readOnly=true)public List<EmployeeOption> employees(){actor.hr();return profiles.findAll().stream().map(p->new EmployeeOption(p.getId(),p.getUserId(),p.getFullName(),p.getStatus())).toList();}
 @Transactional(readOnly=true)public View get(Long id){actor.hr();return view(records.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)));}
 private <T>T read(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("Stored payroll snapshot is invalid",e);}}
 private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Payroll snapshot cannot be encoded",e);}}
 private View view(PayrollReconciliation r){return new View(r.id,r.profileId,r.period,r.status,r.createdBy,r.approvedBy,read(r.reportJson,Report.class),read(r.inputJson,Input.class));}
 private YearMonth period(String value){try{if(value==null||!value.matches("\\d{4}-(0[1-9]|1[0-2])"))throw new IllegalArgumentException();YearMonth ym=YearMonth.parse(value);if(ym.getYear()<2000||ym.getYear()>2100)throw new IllegalArgumentException();return ym;}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Period must be YYYY-MM");}}
 @Transactional(readOnly=true) public List<View> list(String period){actor.hr();period(period);return records.findByPeriodOrderByProfileId(period).stream().map(this::view).toList();}
 @Transactional(readOnly=true) public Report preview(Input input){actor.hr();return compute(input);}
 @Transactional public View prepare(Input input){
  actor.hr();Report report=compute(input);PayrollReconciliation r=records.lockPeriod(input.profileId(),input.period()).orElseGet(PayrollReconciliation::new);
  if("CONSUMED".equals(r.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"Issued payroll is locked; corrections require a separate payroll adjustment");
  r.profileId=input.profileId();r.employeeUserId=report.employeeUserId();r.period=input.period();r.inputJson=write(input);r.reportJson=write(report);r.sourceFingerprint=report.fingerprint();r.status="DRAFT";r.createdBy=actor.id();r.createdAt=OffsetDateTime.now();r.approvedBy=null;r.approvedAt=null;
  PayrollReconciliation saved=records.save(r);audit.record("PAYROLL","PREPARE",saved.id);return view(saved);
 }
 @Transactional public View approve(Long id){
  actor.hr();PayrollReconciliation r=records.lock(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(!"DRAFT".equals(r.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a draft can be approved");
  if(actor.id().equals(r.createdBy)||actor.id().equals(r.employeeUserId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"A different HR/Admin must approve; self-payroll approval is not allowed");
  Report current=compute(read(r.inputJson,Input.class));
  if(!current.fingerprint().equals(r.sourceFingerprint))throw new ResponseStatusException(HttpStatus.CONFLICT,"Attendance, leave, salary or roster changed. Prepare a fresh reconciliation");
  if(!current.issues().isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Resolve all reconciliation issues before approval");
  r.status="APPROVED";r.approvedBy=actor.id();r.approvedAt=OffsetDateTime.now();audit.record("PAYROLL","APPROVE",r.id);return view(records.save(r));
 }
 /** Joins the salary-generation transaction, so the snapshot and salary commit or roll back together. */
 @Transactional public Approved consume(Long profileId,String period){
  actor.hr();PayrollReconciliation r=records.lockPeriod(profileId,period).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Prepare and approve attendance/payroll reconciliation first"));
  if(!"APPROVED".equals(r.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"Payroll reconciliation must be approved and unused");
  Input input=read(r.inputJson,Input.class);YearMonth ym=period(period);
  if(LocalDate.now(zone).isBefore(ym.plusMonths(1).atDay(input.policy().cutoffDay())))throw new ResponseStatusException(HttpStatus.CONFLICT,"Payroll cutoff has not been reached");
  Report report=compute(input);
  if(!report.fingerprint().equals(r.sourceFingerprint)||!report.issues().isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Payroll source changed after approval; re-prepare and re-approve");
  r.status="CONSUMED";r.consumedAt=OffsetDateTime.now();records.save(r);audit.record("PAYROLL","GENERATE",r.id);return new Approved(r.id,r.approvedBy,report,input.policy().cutoffDay());
 }
 public record Approved(Long reconciliationId,String approver,Report report,int cutoffDay){}
 private BigDecimal amount(BigDecimal n){if(n==null)return BigDecimal.ZERO;if(n.signum()<0||n.scale()>2)throw new IllegalArgumentException("Invalid salary structure amount");return n;}
 private Report compute(Input input){
  if(input==null||input.profileId()==null||input.policy()==null||input.overtimeHourlyRate()==null||input.overtimeHourlyRate().signum()<0||input.overtimeHourlyRate().scale()>2||input.overtimeHourlyRate().compareTo(new BigDecimal("1000000"))>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Supply employee, policy, month and approved overtime hourly rate (0 disables overtime payment)");
  approvedPolicies.requireApproved(input.policy(), input.period());
  YearMonth ym=period(input.period());LocalDate first=ym.atDay(1),last=ym.atEndOfMonth();
  EmployeeProfile profile=profiles.findById(input.profileId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Employee profile not found"));Employee employee=profile.getEmployee();
  if(employee==null||employee.getJoiningDate()==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Verified employee record and joining date required");
  SalaryStructure structure=structures.getSalaryStructureByEmployeeProfileId(profile.getId());
  List<String> issues=new ArrayList<>(),source=new ArrayList<>();
  if(structure.getEffectiveFrom()==null||structure.getEffectiveFrom().isAfter(employee.getJoiningDate().isAfter(first)?employee.getJoiningDate():first))issues.add("SALARY_STRUCTURE_NOT_EFFECTIVE_FOR_WHOLE_PERIOD: split effective-dated payroll with HR before generating");
  BigDecimal gross=amount(structure.getBasicSalary()).add(amount(structure.getHra())).add(amount(structure.getConveyance())).add(amount(structure.getMedicalAllowance())).add(amount(structure.getSpecialAllowance())).add(amount(structure.getOtherAllowance()));
  BigDecimal deductions=amount(structure.getPf()).add(amount(structure.getEsi())).add(amount(structure.getProfessionalTax())).add(amount(structure.getTds())).add(amount(structure.getLoanDeduction()));
  source.add("POLICY:"+write(input));source.add("STRUCTURE:"+write(Arrays.asList(structure.getId(),structure.getUpdatedAt(),structure.getEffectiveFrom(),structure.getBasicSalary(),structure.getHra(),structure.getConveyance(),structure.getMedicalAllowance(),structure.getSpecialAllowance(),structure.getOtherAllowance(),structure.getPf(),structure.getEsi(),structure.getProfessionalTax(),structure.getTds(),structure.getLoanDeduction())));
  LocalDate end=null;
  for(EmployeeSeparation separation:separations.findAll())if(Objects.equals(separation.getUserId(),profile.getUserId())&&("APPROVED".equals(separation.getStatus())||"COMPLETED".equals(separation.getStatus()))){end=separation.getLastWorkingDate();source.add("SEPARATION:"+separation.getId()+":"+end+":"+separation.getStatus()+":"+separation.getUpdatedAt());}
  source.add("EMPLOYEE:"+employee.getId()+":"+profile.getUserId()+":"+employee.getJoiningDate()+":"+end+":"+profile.getStatus());
  Map<LocalDate,List<Attendance>> punches=attendance.findByEmployeeAndAiDateRange(employee,first,last).stream().collect(Collectors.groupingBy(a->a.getDate()!=null?a.getDate():a.getAttendanceDate()));
  List<LeaveRequest> approved=leaves.findByEmployeeId(profile.getUserId()).stream().filter(l->l.getStatus()==LeaveStatus.APPROVED&&l.getStartDate()!=null&&l.getEndDate()!=null&&!l.getStartDate().isAfter(last)&&!l.getEndDate().isBefore(first)).toList();
  Set<LocalDate> approvedTimesheets=new HashSet<>();
  for(Timesheet t:timesheets.findByEmployeeIdAndDateBetween(profile.getUserId(),first,last)){source.add("TIME:"+t.getId()+":"+t.getDate()+":"+t.getStatus());if("APPROVED".equalsIgnoreCase(t.getStatus()))approvedTimesheets.add(t.getDate());}
  Set<LocalDate> nonWorking=new HashSet<>();
  for(Holiday h:holidays.findByHolidayDateBetween(first,last))if(!Boolean.FALSE.equals(h.getActive())&&!Boolean.TRUE.equals(h.getOptionalHoliday())){String type=h.getHolidayType()==null?"":h.getHolidayType();if(type.isBlank()||Set.of("PUBLIC","COMPANY").contains(type.toUpperCase(Locale.ROOT))){nonWorking.add(h.getHolidayDate());source.add("HOLIDAY:"+h.getId()+":"+h.getHolidayDate());}}
  Map<LocalDate,ShiftAssignment> roster=new HashMap<>();Set<LocalDate> duplicateShifts=new HashSet<>();
  for(ShiftAssignment a:shifts.findByEmployee_IdOrderByShiftDateDesc(employee.getId()))if(!a.getShiftDate().isBefore(first)&&!a.getShiftDate().isAfter(last)){if(roster.put(a.getShiftDate(),a)!=null)duplicateShifts.add(a.getShiftDate());Shift sh=a.getShift();source.add("SHIFT:"+a.getId()+":"+a.getShiftDate()+":"+a.getStatus()+":"+(sh==null?"null":sh.getId()+":"+sh.getStandardHours()+":"+sh.getStartTime()+":"+sh.getEndTime()+":"+sh.getActive()));}
  for(LeaveRequest l:approved)source.add("LEAVE:"+l.getId()+":"+l.getStartDate()+":"+l.getEndDate()+":"+l.getLeaveType()+":"+l.getLeaveDurationType()+":"+l.getApprovedAt());
  BigDecimal overtimeHours=BigDecimal.ZERO;Set<LocalDate> overtimeDates=new HashSet<>();
  for(OvertimeRequest o:overtime.findByUserIdOrderByWorkDateDescCreatedAtDesc(profile.getUserId()))if(o.getWorkDate()!=null&&!o.getWorkDate().isBefore(first)&&!o.getWorkDate().isAfter(last)&&"APPROVED".equals(o.getStatus())&&"PAYMENT".equals(o.getCompensationMode())){
   if(!overtimeDates.add(o.getWorkDate()))issues.add("MULTIPLE_APPROVED_OVERTIME_REQUESTS:"+o.getWorkDate());
   if(o.getWorkDate().isBefore(employee.getJoiningDate())||(end!=null&&o.getWorkDate().isAfter(end)))issues.add("OVERTIME_OUTSIDE_EMPLOYMENT:"+o.getId());
   List<Attendance> overtimePunches=punches.getOrDefault(o.getWorkDate(),List.of());
   boolean regularDay=input.policy().workingDays().contains(o.getWorkDate().getDayOfWeek())&&!nonWorking.contains(o.getWorkDate());
   double regularHours=regularDay?input.policy().fullDayMinutes()/60.0:0;
   ShiftAssignment overtimeShift=roster.get(o.getWorkDate());
   if(overtimeShift!=null){if("OFF".equals(overtimeShift.getStatus())||"CANCELLED".equals(overtimeShift.getStatus()))regularHours=0;
    else if(overtimeShift.getShift()!=null&&overtimeShift.getShift().getStandardHours()!=null)regularHours=overtimeShift.getShift().getStandardHours();}
   Attendance punch=overtimePunches.size()==1?overtimePunches.get(0):null;
   if(punch==null||punch.getStatus()==null||!Set.of("PRESENT","LATE","HALF_DAY").contains(punch.getStatus().name())||punch.getCheckInTime()==null||punch.getCheckOutTime()==null||
      punch.getWorkingHours()==null||!Double.isFinite(punch.getWorkingHours())||punch.getWorkingHours()<0||punch.getWorkingHours()>24||!Double.isFinite(regularHours)||regularHours<0||
      o.getHours()==null||o.getHours()>Math.max(0,punch.getWorkingHours()-regularHours)+0.000001)
       issues.add("OVERTIME_EXCEEDS_VERIFIED_EXTRA_WORK:"+o.getId());
   source.add("OVERTIME:"+o.getId()+":"+o.getWorkDate()+":"+o.getHours()+":"+o.getReviewerUserId()+":"+o.getReviewedAt());
   if(o.getHours()==null||!Double.isFinite(o.getHours())||o.getHours()<0||o.getHours()>24)issues.add("INVALID_APPROVED_OVERTIME:"+o.getId());else overtimeHours=overtimeHours.add(BigDecimal.valueOf(o.getHours()));
  }
  if(overtimeHours.signum()>0&&input.overtimeHourlyRate().signum()==0)issues.add("APPROVED_OVERTIME_REQUIRES_NONZERO_APPROVED_RATE");
  List<PayrollReconciliationRules.Day> days=new ArrayList<>();Map<LocalDate,PayrollReconciliationRules.Decision> decisions=input.decisions()==null?Map.of():input.decisions();
  for(LocalDate d:decisions.keySet())if(d==null||d.isBefore(first)||d.isAfter(last))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Override date is outside the payroll month");
  for(LocalDate d=first;!d.isAfter(last);d=d.plusDays(1)){
   List<String> dayIssues=new ArrayList<>();if(duplicateShifts.contains(d))dayIssues.add("DUPLICATE_SHIFT_ASSIGNMENTS");boolean employed=!d.isBefore(employee.getJoiningDate())&&(end==null||!d.isAfter(end));
   boolean scheduled=input.policy().workingDays().contains(d.getDayOfWeek())&&!nonWorking.contains(d);int required=input.policy().fullDayMinutes();ShiftAssignment assignment=roster.get(d);
   if(assignment!=null){if(("OFF".equals(assignment.getStatus())||"CANCELLED".equals(assignment.getStatus())))scheduled=false;else{scheduled=true;Shift sh=assignment.getShift();if(sh==null||Boolean.FALSE.equals(sh.getActive())||sh.getStandardHours()==null||!Double.isFinite(sh.getStandardHours()))dayIssues.add("INVALID_SHIFT");else required=(int)Math.round(sh.getStandardHours()*60);}}
   List<Attendance> dayPunches=punches.getOrDefault(d,List.of());Attendance a=dayPunches.size()==1?dayPunches.get(0):null;
   for(Attendance row:dayPunches)source.add("ATTENDANCE:"+row.getId()+":"+d+":"+row.getStatus()+":"+row.getCheckInTime()+":"+row.getCheckOutTime()+":"+row.getCheckOutDateTime()+":"+row.getCorrectedAt()+":"+row.getWorkingHours()+":"+row.getRegularizedAt()+":"+row.getRegularizedByRequestId());
   Integer minutes=a==null||a.getWorkingHours()==null||!Double.isFinite(a.getWorkingHours())?null:(int)Math.round(a.getWorkingHours()*60);
   double paid=0,unpaid=0,project=0;Set<String> halfSlots=new HashSet<>();
   for(LeaveRequest l:approved)if(!d.isBefore(l.getStartDate())&&!d.isAfter(l.getEndDate())){
    String duration=l.getLeaveDurationType()==null?"FULL_DAY":l.getLeaveDurationType();boolean half=Set.of("FIRST_HALF","SECOND_HALF").contains(duration);double units=half?0.5:1;
    if(half&&!l.getStartDate().equals(l.getEndDate()))dayIssues.add("MULTIDAY_HALF_LEAVE_REQUIRES_REVIEW");
    if(half&&!halfSlots.add(duration))dayIssues.add("OVERLAPPING_HALF_LEAVE");
    String treatment=input.policy().leaveTreatment().get(l.getLeaveType()==null?"":l.getLeaveType().trim().toUpperCase(Locale.ROOT));
    if(treatment==null)dayIssues.add("UNCLASSIFIED_LEAVE_TYPE:"+l.getLeaveType());else if(treatment.equals("PAID"))paid+=units;else if(treatment.equals("UNPAID"))unpaid+=units;else project+=units;
   }
   PayrollReconciliationRules.Day result=PayrollReconciliationRules.day(new PayrollReconciliationRules.Input(d,scheduled,employed,required,a==null?null:a.getStatus()==null?null:a.getStatus().name(),minutes,a!=null&&a.getCheckInTime()!=null&&a.getCheckOutTime()==null,dayPunches.size(),approvedTimesheets.contains(d),paid,unpaid,project,dayIssues,decisions.get(d)),input.policy());
   days.add(result);for(String issue:result.issues())issues.add(d+":"+issue);
  }
  PayrollReconciliationRules.Totals totals=null;
  try{totals=PayrollReconciliationRules.totals(days,gross,deductions,overtimeHours,input.overtimeHourlyRate());}catch(IllegalArgumentException e){issues.add(e.getMessage());}
  Collections.sort(source);String fingerprint=IntegrationCrypto.sha256(String.join("\n",source));
  return new Report(profile.getUserId(),input.period(),input.policy().reference(),fingerprint,List.copyOf(days),totals,overtimeHours,List.copyOf(issues));
 }
}
