package EmployeeManagementSystem.service.impl;

import EmployeeManagementSystem.dto.CalendarEventDTO;
import EmployeeManagementSystem.entity.Attendance;
import EmployeeManagementSystem.entity.Holiday;
import EmployeeManagementSystem.entity.LeaveRequest;
import EmployeeManagementSystem.entity.Timesheet;
import EmployeeManagementSystem.enums.LeaveStatus;
import EmployeeManagementSystem.repository.AttendanceRepository;
import EmployeeManagementSystem.repository.HolidayRepository;
import EmployeeManagementSystem.repository.LeaveRepository;
import EmployeeManagementSystem.repository.TimesheetRepository;
import EmployeeManagementSystem.service.AttendanceCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceCalendarServiceImpl
        implements AttendanceCalendarService {

    private final AttendanceRepository attendanceRepository;
    private final LeaveRepository leaveRepository;
    private final HolidayRepository holidayRepository;

    @Override
    public List<CalendarEventDTO> getCalendarEvents(
            String employeeId,
            int year,
            int month
    ) {

        YearMonth yearMonth = YearMonth.of(year, month);

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<Attendance> attendance = attendanceRepository.findAll().stream()
                .filter(a -> a.getEmployee() != null && a.getEmployee().getRegisterEmployee() != null
                        && employeeId.equals(a.getEmployee().getRegisterEmployee().getUserId()))
                .filter(a -> a.getDate() != null && !a.getDate().isBefore(startDate) && !a.getDate().isAfter(endDate))
                .toList();

        List<LeaveRequest> approvedLeaves =
                leaveRepository
                        .findByEmployeeIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                                employeeId,
                                endDate,
                                startDate
                        )
                        .stream()
                        .filter(leave ->
                                leave.getStatus() == LeaveStatus.APPROVED
                        )
                        .toList();

        List<Holiday> holidays =
                holidayRepository.findByHolidayDateBetween(
                        startDate,
                        endDate
                ).stream()
                .filter(h -> !Boolean.FALSE.equals(h.getActive()))
                .filter(h -> !Boolean.TRUE.equals(h.getOptionalHoliday()))
                .filter(h -> {
                    String type = h.getHolidayType() == null ? "" : h.getHolidayType().toUpperCase(Locale.ENGLISH);
                    return type.isBlank() || "PUBLIC".equals(type) || "COMPANY".equals(type);
                })
                .toList();

        Set<LocalDate> presentDates = attendance.stream()
                .filter(a -> a.getDate() != null)
                .filter(a -> a.getStatus() == EmployeeManagementSystem.enums.AttendanceStatus.PRESENT
                        || a.getStatus() == EmployeeManagementSystem.enums.AttendanceStatus.LATE
                        || a.getStatus() == EmployeeManagementSystem.enums.AttendanceStatus.HALF_DAY)
                .map(Attendance::getDate)
                .collect(Collectors.toSet());

        Set<LocalDate> holidayDates =
                holidays.stream()
                        .map(Holiday::getHolidayDate)
                        .collect(Collectors.toSet());

        Set<LocalDate> leaveDates = new HashSet<>();

        for (LeaveRequest leave : approvedLeaves) {

            LocalDate currentDate = leave.getStartDate();

            while (!currentDate.isAfter(leave.getEndDate())) {

                leaveDates.add(currentDate);

                currentDate = currentDate.plusDays(1);
            }
        }

        System.out.println("Present Dates : " + presentDates);
        System.out.println("Leave Dates   : " + leaveDates);
        System.out.println("Holiday Dates : " + holidayDates);
        System.out.println("==================================");

        List<CalendarEventDTO> events = new ArrayList<>();

        LocalDate currentDate = startDate;

        LocalDate today = LocalDate.now();

        while (!currentDate.isAfter(endDate)) {

            String title;
            String color;

            if (leaveDates.contains(currentDate)) {

                title = "Leave";
                color = "#c47b2e";
            }
            else if (holidayDates.contains(currentDate)) {

                title = "Holiday";
                color = "#2b6cb0";
            }
            else if (
                    currentDate.getDayOfWeek() == DayOfWeek.SATURDAY
                            || currentDate.getDayOfWeek() == DayOfWeek.SUNDAY
            ) {

                title = "Weekend";
                color = "#475569";
            }
            else if (currentDate.isAfter(today)) {


                currentDate = currentDate.plusDays(1);
                continue;
            }
            else if (presentDates.contains(currentDate)) {

                title = "Present";
                color = "#2b7a4b";
            }
            else {

                title = "Absent";
                color = "#d64545";
            }

            events.add(
                    new CalendarEventDTO(
                            title,
                            currentDate.toString(),
                            color
                    )
            );

            currentDate = currentDate.plusDays(1);
        }
        return events;
    }
}