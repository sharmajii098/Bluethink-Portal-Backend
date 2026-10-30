package EmployeeManagementSystem.service;

import java.util.Locale;

/** Deterministic, intentionally narrow intent selection for verified portal record lookups. */
public final class AiPortalIntent {
    private AiPortalIntent() {}

    public enum Module { SUPPORT, ASSETS, EXPENSES, TRAINING, PERFORMANCE, RECRUITMENT, MEETINGS, NONE }

    public static Module detect(String question) {
        if (question == null) return Module.NONE;
        String q = " " + question.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
        if (has(q, "ticket", "tickets", "helpdesk", "support request", "support requests")) return Module.SUPPORT;
        if (has(q, "asset", "assets", "equipment", "assigned laptop", "asset request")) return Module.ASSETS;
        if (has(q, "expense", "expenses", "reimbursement", "reimbursements", "expense claim", "expense claims")) return Module.EXPENSES;
        if (has(q, "training", "enrollment", "enrollments", "my courses", "available courses", "training course")) return Module.TRAINING;
        if (has(q, "performance", "appraisal", "appraisals", "review cycle", "my goals", "team reviews")) return Module.PERFORMANCE;
        if (has(q, "recruitment", "vacancies", "vacancy", "job opening", "job openings", "open positions", "hiring")) return Module.RECRUITMENT;
        if (has(q, "meeting", "meetings", "meeting calendar", "upcoming calendar events")) return Module.MEETINGS;
        return Module.NONE;
    }

    private static boolean has(String padded, String... phrases) {
        for (String phrase : phrases) if (padded.contains(" " + phrase + " ")) return true;
        return false;
    }
}
