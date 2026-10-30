package EmployeeManagementSystem.service;

import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Strictly extractive policy lookup over the same EFFECTIVE records returned
 * by /api/policies for the signed-in user. No file-system reading, LLM prompt,
 * or unpublished future policy access. A policy citation is its database ID.
 */
@Service
public class AiPolicyKnowledgeService {
    private static final Set<String> IGNORED = Set.of(
            "policy", "policies", "company", "portal", "bluethink", "hr", "employee", "employees",
            "show", "tell", "about", "what", "when", "where", "which", "how", "does", "do", "my", "our",
            "are", "the", "this", "that", "have", "has", "with", "please", "details", "rules", "rule",
            "find", "search", "for", "and", "can", "you", "is", "in", "of", "a", "an", "me", "all",
            "handbook", "effective", "on", "to", "give", "summary", "summarize", "explain", "related");
    private final PolicyService policies;

    public AiPolicyKnowledgeService(PolicyService policies) { this.policies = policies; }

    public static boolean handles(String question) {
        if (question == null) return false;
        String q = " " + question.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
        return q.contains(" policy ") || q.contains(" policies ") || q.contains(" handbook ");
    }

    public String answer(String question, String verifiedAccountId) {
        if (verifiedAccountId == null || verifiedAccountId.isBlank())
            throw new org.springframework.security.access.AccessDeniedException("Verified account required");
        List<Map<String, Object>> visible = policies.listForUser(verifiedAccountId);
        if (visible == null || visible.isEmpty()) return "No effective company policies are visible to your account.";

        List<String> terms = Arrays.stream(question.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(s -> s.length() >= 3 && !IGNORED.contains(s)).distinct().limit(12).toList();
        boolean listing = terms.isEmpty();
        List<Hit> hits = new ArrayList<>();
        for (Map<String, Object> row : visible) {
            if (row == null) continue;
            // Defense in depth, even if a future PolicyService change forgets the effective-date filter.
            if (row.get("effectiveDate") instanceof LocalDate date && date.isAfter(LocalDate.now())) continue;
            String title = clean(row.get("title"));
            String category = clean(row.get("category"));
            String description = clean(row.get("description"));
            if (title.isBlank()) continue;
            int score = 0;
            String inTitle = title.toLowerCase(Locale.ROOT);
            String inCategory = category.toLowerCase(Locale.ROOT);
            String inDescription = description.toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (containsWord(inTitle, term)) score += 5;
                if (containsWord(inCategory, term)) score += 3;
                if (containsWord(inDescription, term)) score += 1;
            }
            if (listing || score > 0) hits.add(new Hit(row, title, category, description, score));
        }
        if (hits.isEmpty()) return "I could not find an effective company policy matching that question in the policies visible to your account. Check the Policies module or contact HR; I will not guess company rules.";
        hits.sort(Comparator.comparingInt(Hit::score).reversed()
                .thenComparing(h -> h.title().toLowerCase(Locale.ROOT)));
        StringBuilder out = new StringBuilder(listing ? "Effective company policies visible to you:" :
                "Matching company policies (excerpts from stored portal text, not AI-generated rules):");
        for (Hit hit : hits.stream().limit(listing ? 12 : 3).toList()) {
            Object id = hit.row().get("id");
            Object effective = hit.row().get("effectiveDate");
            out.append("\n\n- ").append(limit(hit.title(), 100))
                    .append(" | Policy #").append(id == null ? "unavailable" : id)
                    .append(" | ").append(limit(hit.category(), 50));
            if (effective != null) out.append(" | effective ").append(effective);
            if (!listing) {
                String snippet = excerpt(hit.description(), terms, 430);
                out.append("\n  Source excerpt: ").append(snippet.isBlank() ? "Description not available in this record." : snippet);
            }
        }
        if (hits.size() > (listing ? 12 : 3)) out.append("\n\nAdditional matching records are available in Policies.");
        return out.append("\n\nSource: BlueThink Portal > Policies (effective records visible to your account). " +
                "Open the policy by its ID to read the full authoritative text; attached files are not searched by this assistant.").toString();
    }

    private record Hit(Map<String, Object> row, String title, String category, String description, int score) {}
    private static boolean containsWord(String text, String term) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(term) + "(?![a-z0-9])").matcher(text).find();
    }
    private static String clean(Object text) {
        if (text == null) return "";
        return text.toString().replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
    }
    private static String limit(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
    private static String excerpt(String description, List<String> terms, int max) {
        if (description.isBlank()) return "";
        int best = 0;
        String lower = description.toLowerCase(Locale.ROOT);
        for (String term : terms) {
            Matcher matcher = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(term) + "(?![a-z0-9])").matcher(lower);
            if (matcher.find()) { best = Math.max(0, matcher.start() - 90); break; }
        }
        int end = Math.min(description.length(), best + max);
        return (best > 0 ? "..." : "") + description.substring(best, end)
                + (end < description.length() ? "..." : "");
    }
}
