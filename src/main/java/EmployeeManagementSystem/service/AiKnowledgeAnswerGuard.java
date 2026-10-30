package EmployeeManagementSystem.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Last-mile guard for model-generated company-knowledge answers.
 * The caller passes the number of already authorized/retrieved sources; this
 * helper never retrieves documents or modifies access rights.
 */
final class AiKnowledgeAnswerGuard {
    private static final Pattern CITATION = Pattern.compile("\\[S([0-9]+)\\]");
    private static final int MAX_ANSWER_CHARS = 12000;

    private AiKnowledgeAnswerGuard() {}

    static String verifiedOrFallback(String modelAnswer, int authorizedSourceCount, String question) {
        String fallback = AiKnowledgeReplyPolicy.unverifiedAnswer(question);
        if (authorizedSourceCount < 1 || authorizedSourceCount > 20 || modelAnswer == null ||
                modelAnswer.isBlank() || modelAnswer.length() > MAX_ANSWER_CHARS) return fallback;
        Matcher citation = CITATION.matcher(modelAnswer);
        boolean hasReference = false;
        while (citation.find()) {
            hasReference = true;
            int number;
            try { number = Integer.parseInt(citation.group(1)); }
            catch (NumberFormatException invalid) { return fallback; }
            if (number < 1 || number > authorizedSourceCount) return fallback;
        }
        if (!hasReference) return fallback;
        // A lone valid reference somewhere in a response must not authorize other,
        // uncited paragraphs or bullet points. This is only a structural check;
        // checking truth still requires a human to read the underlying source.
        for (String line : modelAnswer.split("\\R")) {
            String candidate = line.trim();
            if (candidate.isEmpty()) continue;
            // Reject uncited standalone sentences even when another sentence
            // on the same line happened to have a valid citation.
            for (String sentence : candidate.split("(?<=[.!?])\\s+(?=\\S)")) {
                if (!CITATION.matcher(sentence).find()) return fallback;
            }
        }
        return modelAnswer.trim();
    }
}
