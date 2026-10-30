package EmployeeManagementSystem.service;

import java.util.regex.Pattern;

/**
 * Presentation guidance for grounded company-knowledge answers.
 * Deliberately pure and independent of the AI provider or data access layer.
 * Never attempts to derive a policy rule from the wording of a question.
 */
final class AiKnowledgeReplyPolicy {
    private static final Pattern HINGLISH = Pattern.compile(
            "(?iu)(?<![a-z])(?:mujhe|mera|meri|mere|hum|hamara|aap|aapka|batao|btao|" +
            "kya|kaise|kitni|kitna|kitne|kab|kaun|kyun|chhutti|chutti|" +
            "milti|milega|milegi|hain|hai|nahi|nahin|chahiye)(?![a-z])");

    private AiKnowledgeReplyPolicy() {}

    static boolean hinglish(String question) {
        return question != null && HINGLISH.matcher(question).find();
    }

    static String responseInstructions(String question) {
        if (hinglish(question)) {
            return "Answer in natural Hinglish using Latin script, matching the user's language. " +
                    "Keep official policy terms and names unchanged. Cite the provided [S#] sources. " +
                    "If the excerpts do not explicitly support the answer, say in Hinglish that " +
                    "the approved documents do not provide that information; do not guess.";
        }
        return "Answer in clear English and cite the provided [S#] sources. " +
                "If the excerpts do not explicitly support an answer, say that the approved " +
                "documents do not provide the information; do not guess.";
    }

    static String unverifiedAnswer(String question) {
        return hinglish(question)
                ? "Approved company documents se is jawab ko source ke saath verify nahi kar saka. " +
                  "Kripya document aur cited passages check karein."
                : "The AI answer could not be verified against the retrieved source references. " +
                  "Please check the approved document passages.";
    }

    static String noAuthorizedSources(String question) {
        return hinglish(question)
                ? "Aapke role aur department ke liye koi approved company knowledge document available nahi hai."
                : "No published company knowledge is available for your role and department.";
    }

    static String noRelevantSources(String question) {
        return hinglish(question)
                ? "Mujhe aapke sawaal ka jawab approved company documents mein nahi mila. " +
                  "Main company policy ke rules guess nahi karunga."
                : "No sufficiently relevant source was found in the authorized company knowledge. Try a more specific topic.";
    }
}
