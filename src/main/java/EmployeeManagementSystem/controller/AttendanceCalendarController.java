package EmployeeManagementSystem.controller;

import EmployeeManagementSystem.dto.CalendarEventDTO;
import EmployeeManagementSystem.service.AttendanceCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class AttendanceCalendarController {

    private final AttendanceCalendarService attendanceCalendarService;

    @GetMapping("/{employeeId}")
    public List<CalendarEventDTO> getCalendarEvents(
            @PathVariable String employeeId,
            @RequestParam int year,
            @RequestParam int month
    ) {

        return attendanceCalendarService.getCalendarEvents(
                employeeId,
                year,
                month
        );
    }
}