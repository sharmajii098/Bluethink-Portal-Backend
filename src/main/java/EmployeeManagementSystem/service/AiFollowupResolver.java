package EmployeeManagementSystem.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/** Stateless, allowlisted follow-ups using only the current on-screen chat context.
 * Never carry account/employee identifiers or an old saved answer across chats.
 * All expanded questions still pass through AiAssistantService's live role checks.
 */
public final class AiFollowupResolver {
    private AiFollowupResolver() {}

    public static String resolve(String question, String previousOnScreenQuestion) {
        if (question == null) return null;
        String q = question.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        boolean lastWeek = q.matches("^(what about|and|show( me)?) last week[?.! ]*$");
        boolean thisWeek = q.matches("^(what about|and|show( me)?) this week[?.! ]*$");
        boolean pending = q.matches("^(show( me)? )?only pending( records| timesheets)?[?.! ]*$");
        if (!(lastWeek || thisWeek || pending) || previousOnScreenQuestion == null || previousOnScreenQuestion.length() > 2000)
            return question;
        String previous = previousOnScreenQuestion.toLowerCase(Locale.ROOT).trim();
        // A previous message must be an explicit self/team records request, not an
        // unrelated prompt. Never inherit a named employee or employee ID.
        if (previous.matches("(?s).*(\\bemp[0-9]{3,}\\b|other employee|all employees|another employee|colleague).*")) return question;
        boolean team = previous.matches("(?s).*\\b(my team|our team|direct reports)\\b.*");
        boolean own = previous.matches("(?s).*\\b(my|mine|me)\\b.*");
        if (!team && !own) return question;
        boolean timesheets = previous.contains("timesheet");
        boolean attendance = previous.contains("attendance");
        if (!timesheets && !attendance) return question;
        String module = timesheets ? "timesheets" : "attendance";
        if (pending && !timesheets) return question;
        String subject = team ? "my team" : "my";
        LocalDate start, end;
        if (lastWeek || thisWeek) {
            start = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (lastWeek) start = start.minusWeeks(1);
            end = start.plusDays(6);
        } else {
            end = LocalDate.now(); start = end.minusDays(30);
        }
        return "Show " + subject + (pending ? " pending" : "") + " " + module + " from " + start + " to " + end + ".";
    }
}
