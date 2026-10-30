package EmployeeManagementSystem.service;

import java.util.Locale;

/** Exact, non-personal guidance. Called only after database-backed role verification. */
public final class AiQuickReply {
    private AiQuickReply() {}

    public static String answer(String message, String role) {
        if (message == null) return null;
        String q = message.toLowerCase(Locale.ROOT).trim().replaceAll("[?!.,]+$", "").replaceAll("\\s+", " ");
        if (q.matches("(hi|hello|hey|good morning|good afternoon|good evening)( bluethink| ai)?"))
            return "Hello! I can help with your BlueThink workspace. Ask about your leave balance, attendance, timesheets, payslip, projects, policies or support tickets. I use the records your role permits you to see. What would you like to check?";
        if (q.matches("(thanks|thank you|thank you very much|thankyou)"))
            return "You're welcome. What else would you like to check in your workspace?";
        if (q.matches("(how do i|how can i|where can i|where do i|how to) (apply for leave|apply leave|request leave|submit a leave request)( on (the )?(bluethink )?portal)?")) {
            if (isAdmin(role)) return "Your Admin workspace does not include My Requests → Leave, so it cannot submit a personal leave request there. Open Admin → Leave to review authorized leave records. For a personal request, use BlueThink AI Actions → Leave request if permitted, review the preview and explicitly confirm, or contact HR about the correct process. Never use a different employee's account.";
            return "Open My Requests → Leave, choose your dates and leave category, review the details, then submit. You can also use AI Actions → Leave request and confirm the preview. Your normal approval workflow applies. Ask ‘my leave balance’ to check your available days.";
        }
        if (q.matches("(how do i|how can i|where can i|where do i|how to) (submit a timesheet|fill my timesheet|add a timesheet|log work hours)( on (the )?(bluethink )?portal)?")) {
            if (isAdmin(role)) return "Your Admin workspace does not include My Requests → Timesheets. Open Admin → Timesheets to view the authorized timesheet workspace. For personal timesheet submission access, contact HR or your portal administrator; do not use another account.";
            return "Open My Requests → Timesheets to enter your date, project, work description and hours, then submit for review. You can also prepare a timesheet in AI Actions and confirm its preview. Ask ‘my timesheets’ to check your records.";
        }
        if (q.matches("(where is|how do i open|how can i open) (the )?(login map|location map|login locations)"))
            return "Open Login Locations from your workspace navigation where available. Select a date and employee, then Load map. It shows the location recorded at sign-in, not the employee's live location. Visibility follows your current role.";
        if (q.matches("(how do i|how can i) (customize|customise) (my )?(dashboard|workspace)"))
            return "Use Activity first or Insights first on your dashboard to reorder sections. Open Settings for content width, spacing, text size and sidebar width. Dashboard layout is saved for your account and role on this browser.";
        return null; // Never substitute generic guidance for a request for actual records.
    }

    private static boolean isAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role);
    }
}
