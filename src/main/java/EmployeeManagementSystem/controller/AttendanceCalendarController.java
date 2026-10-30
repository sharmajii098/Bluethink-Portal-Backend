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
    private final EmployeeManagementSystem.service.RequestScopeService scope;

    @GetMapping("/{employeeId}")
    public List<CalendarEventDTO> getCalendarEvents(
            org.springframework.security.core.Authentication auth,
            @PathVariable String employeeId,
            @RequestParam int year,
            @RequestParam int month
    ) {

        String role = auth.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", ""))
                .findFirst().orElse("");
        if (!auth.getName().equals(employeeId)) scope.assertCanManageUser(role, auth.getName(), employeeId);
        if (month < 1 || month > 12 || year < 1900 || year > 2100)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid calendar month/year");
        return attendanceCalendarService.getCalendarEvents(
                employeeId,
                year,
                month
        );
    }
}