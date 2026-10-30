package EmployeeManagementSystem.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detects generic company approval/procedure questions before conversational AI.
 * Personal request history or the identity of a specific employee's reviewer is not
 * a policy question; those requests must remain in the scoped record readers.
 */
public final class AiOfficialProcedureIntent {
    private AiOfficialProcedureIntent() {}

    private static final Pattern PROCESS = Pattern.compile(
            "\\b(approval|approvals|approve|approves|approved|approving|approver|approvers|" +
            "process|procedure|procedures|workflow|steps|signoff|sign off|signs off|" +
            "authorise|authorises|authorisation|authorize|authorizes|authorization|" +
            "reviewer|reviewers|decision maker|decision makers|escalation|routing)\\b");
    private static final Pattern COMPANY_QUESTION = Pattern.compile(
            "\\b(official|company|bluethink|portal|employee|employees|who|whose|which|" +
            "how|what|explain|describe|responsible|required|chain)\\b");
    private static final Pattern PERSONAL_STATUS = Pattern.compile(
            "\\b(status|balance|pending|history|requests|records|last|recent)\\b");
    private static final Pattern DIRECT_STATUS = Pattern.compile(
            "^(show|list|count|find|display|get) (me |my |the |all )?.*");
    private static final Pattern OWN_DECISION = Pattern.compile(
            "(?s).*(who approved my|who rejected my|why was my|who is my approver|" +
            "who is my reviewer|who approves my|my assigned approver|my specific reviewer).*");

    public static boolean handles(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String q = normalize(raw).trim();
        if (DIRECT_STATUS.matcher(q).matches()) return false;
        if (OWN_DECISION.matcher(q).matches()) return false;
        if ((q.contains(" my ") || q.startsWith("my ") || q.contains(" team "))
                && PERSONAL_STATUS.matcher(q).find()) return false;
        if (q.matches("(?s).*\\b(my|team)\\s+(leave|timesheet|expense|overtime)\\s+(approver|reviewer).*"))
            return false;
        return topic(q) != null && PROCESS.matcher(q).find()
                && (COMPANY_QUESTION.matcher(q).find()
                    || q.matches("^(leave|leaves|timesheet|timesheets|expense|reimbursement|" +
                            "overtime|hr service|approver|approvers|reviewer|reviewers).*"));
    }

    public static String policyQuery(String raw) {
        String subject = topic(normalize(raw));
        return (subject == null ? "company" : subject) + " policy";
    }

    /** A draft's title or a model's answer does not establish approval. */
    public static String policyOnlyAnswer(String raw, String visiblePolicyResults) {
        String subject = topic(normalize(raw));
        String label = subject == null ? "company" : subject;
        String preface = "I cannot verify the full official " + label + " approval process or its final decision-maker from the available excerpts alone. "
                + "The role-visible policy records below may be relevant. Open each Policy ID in BlueThink Portal > Policies "
                + "to read the complete text and check its approval status. Proposed, draft, example, or pending-approval language is not an approved rule. "
                + "If the procedure remains unclear, contact authorized HR for the approved process.";
        return preface + "\n\n" + (visiblePolicyResults == null ? "No policy lookup was available." : visiblePolicyResults);
    }

    private static String topic(String q) {
        if (q.matches("(?s).*\\b(leaves?|time off|vacation)\\b.*")) return "leave";
        if (q.matches("(?s).*\\btimesheets?\\b.*")) return "timesheet";
        if (q.matches("(?s).*\\b(expenses?|reimbursements?|claims?)\\b.*")) return "expense";
        if (q.matches("(?s).*\\bovertime\\b.*")) return "overtime";
        if (q.contains("hr service")) return "hr service";
        return null;
    }

    private static String normalize(String raw) {
        return " " + raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }
}
