package EmployeeManagementSystem.service;

import java.util.regex.Pattern;

/** Marks visibly incomplete model responses without fabricating missing content. */
public final class AiAnswerCompleteness {
    private AiAnswerCompleteness() {}
    private static final String NOTICE = "[AI response may be incomplete. Ask for a shorter answer or retry.]";
    private static final Pattern ORPHAN_NUMBER = Pattern.compile("(?:\\d+|[A-Za-z])[.)]\\s*");
    private static final Pattern DANGLING_END = Pattern.compile("(?i)(?:\\b(?:and|or|the|a|an|to|with|for|because|including)\\b|[,;:]|[-–])\\s*$");

    public static String markIfTruncated(String response) {
        if (response == null) return null;
        String text = response.stripTrailing();
        if (text.isEmpty()) return text;
        int lastBreak = text.lastIndexOf('\n');
        String lastLine = text.substring(lastBreak + 1).trim();
        if (ORPHAN_NUMBER.matcher(lastLine).matches()) {
            String previous = lastBreak < 0 ? "" : text.substring(0, lastBreak).stripTrailing();
            return (previous.isEmpty() ? "" : previous + "\n\n") + NOTICE;
        }
        if (text.endsWith("```") || text.endsWith(".") || text.endsWith("!") || text.endsWith("?")) return text;
        if (DANGLING_END.matcher(text).find()) return text + "\n\n" + NOTICE;
        // A long response without terminal punctuation may have hit the model's token limit.
        long words = Pattern.compile("\\s+").splitAsStream(text).count();
        if (words > 45 && !text.endsWith("\"") && !text.endsWith("'"))
            return text + "\n\n" + NOTICE;
        return text;
    }
}
