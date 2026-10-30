package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Read-only AI bridge for additional employee self-service modules.
 * The caller must pass the authenticated, DB-verified account ID/role, never an ID
 * from the question. Deliberately does not expose request bodies, HR comments,
 * document bytes, private values, medical details, or other employees' records.
 */
@Service
public class AiSelfServiceRecords {
    public enum Topic {
        HR_REQUEST, TRAVEL, OVERTIME, DATA_CHANGE, LETTER, DOCUMENT,
        ATTENDANCE_CORRECTION, ACCESS_CARD, RECOGNITION, SUGGESTION, NONE
    }

    private final HrServiceRequestRepository hrRequests;
    private final TravelRequestRepository travel;
    private final OvertimeRequestRepository overtime;
    private final EmployeeDataChangeRequestRepository changes;
    private final EmployeeLetterRequestRepository letters;
    private final EmployeeDocumentRepository documents;
    private final AttendanceRegularizationRepository corrections;
    private final EmployeeAccessCardRequestRepository cards;
    private final EmployeeRecognitionRepository recognition;
    private final EmployeeSuggestionRepository suggestions;

    public AiSelfServiceRecords(HrServiceRequestRepository hrRequests,
            TravelRequestRepository travel, OvertimeRequestRepository overtime,
            EmployeeDataChangeRequestRepository changes, EmployeeLetterRequestRepository letters,
            EmployeeDocumentRepository documents, AttendanceRegularizationRepository corrections,
            EmployeeAccessCardRequestRepository cards, EmployeeRecognitionRepository recognition,
            EmployeeSuggestionRepository suggestions) {
        this.hrRequests = hrRequests;
        this.travel = travel;
        this.overtime = overtime;
        this.changes = changes;
        this.letters = letters;
        this.documents = documents;
        this.corrections = corrections;
        this.cards = cards;
        this.recognition = recognition;
        this.suggestions = suggestions;
    }

    static String words(String q) {
        return " " + (q == null ? "" : q.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim()) + " ";
    }

    private static boolean contains(String normalized, String... phrases) {
        for (String phrase : phrases) {
            if (normalized.contains(" " + phrase + " ")) return true;
        }
        return false;
    }

    public static Topic detect(String question) {
        String q = words(question);
        if (contains(q, "hr service request", "hr service requests", "hr help request", "hr requests", "hr request")) return Topic.HR_REQUEST;
        if (contains(q, "travel request", "travel requests", "business travel", "travel approval")) return Topic.TRAVEL;
        if (contains(q, "overtime", "overtime request", "overtime requests", "comp off", "compensatory off")) return Topic.OVERTIME;
        if (contains(q, "profile change request", "profile change requests", "data change request", "data change requests", "profile correction", "personal data correction")) return Topic.DATA_CHANGE;
        if (contains(q, "letter request", "letter requests", "employment letter", "experience letter", "relieving letter", "salary certificate")) return Topic.LETTER;
        if (contains(q, "my documents", "my document", "employee documents", "uploaded documents", "document verification", "document status")) return Topic.DOCUMENT;
        if (contains(q, "attendance regularization", "attendance correction", "attendance corrections", "missing punch correction", "regularization requests")) return Topic.ATTENDANCE_CORRECTION;
        if (contains(q, "access card", "access cards", "id card request", "id card replacement")) return Topic.ACCESS_CARD;
        if (contains(q, "recognition", "recognitions", "award nominations", "my awards")) return Topic.RECOGNITION;
        if (contains(q, "suggestion", "suggestions", "employee feedback request", "submitted feedback")) return Topic.SUGGESTION;
        return Topic.NONE;
    }

    @Transactional(readOnly = true)
    public String answer(String question, String verifiedAccountId, String verifiedRole, Employee verifiedEmployee) {
        Topic topic = detect(question);
        if (topic == Topic.NONE) return null;
        if (verifiedAccountId == null || verifiedAccountId.isBlank() || verifiedRole == null
                || verifiedRole.isBlank() || verifiedEmployee == null)
            throw new AccessDeniedException("Verified employee session required");
        String q = words(question);
        // These are intentionally self-only views even for privileged roles. Separate portal
        // screens enforce reviewer permissions for team/organization-wide information.
        if (contains(q, "my team", "team members", "direct reports", "all employees",
                "every employee", "other employees", "another employee", "other employee",
                "everyone", "colleagues", "someone else")) {
            return "This AI view shows only your own " + label(topic)
                    + ". Use the portal's permission-controlled module for other employees' records.";
        }
        String id = verifiedAccountId;
        return switch (topic) {
            case HR_REQUEST -> format("Your HR service requests", hrRequests.findByUserIdOrderByCreatedAtDesc(id),
                    r -> clean(r.getRequestNumber(), 34) + " | " + clean(r.getCategory(), 45) + " | " + clean(r.getStatus(), 25));
            case TRAVEL -> format("Your travel requests", travel.findByUserIdOrderByStartDateDesc(id),
                    r -> clean(r.getDestination(), 65) + " | " + date(r.getStartDate()) + " to " + date(r.getEndDate()) + " | " + clean(r.getStatus(), 25));
            case OVERTIME -> format("Your overtime requests", overtime.findByUserIdOrderByWorkDateDescCreatedAtDesc(id),
                    r -> date(r.getWorkDate()) + " | " + (r.getHours() == null ? "hours not recorded" : r.getHours() + " hours")
                            + " | " + clean(r.getStatus(), 25));
            case DATA_CHANGE -> format("Your profile/data change requests", changes.findByUserIdOrderByCreatedAtDesc(id),
                    r -> clean(r.getRequestNumber(), 34) + " | field " + clean(r.getFieldName(), 55) + " | " + clean(r.getStatus(), 25));
            case LETTER -> format("Your HR letter requests", letters.findByEmployeeUserIdOrderByCreatedAtDesc(id),
                    r -> clean(r.getRequestNumber(), 34) + " | " + clean(r.getLetterType(), 50) + " | " + clean(r.getStatus(), 25));
            case DOCUMENT -> format("Your uploaded document statuses", documents.findByEmployeeIdOrderByUploadedAtDesc(id),
                    r -> "Document #" + r.getId() + " | " + clean(r.getCategory(), 50) + " | "
                            + clean(r.getStatus(), 25) + " | expiry " + date(r.getExpiryDate()));
            case ATTENDANCE_CORRECTION -> format("Your attendance correction requests",
                    corrections.findByEmployeeUserIdOrderByCreatedAtDesc(id),
                    r -> clean(r.getRequestNumber(), 34) + " | " + date(r.getAttendanceDate()) + " | " + clean(r.getStatus(), 25));
            case ACCESS_CARD -> format("Your access-card requests", cards.findByUserIdOrderByCreatedAtDesc(id),
                    r -> clean(r.getRequestNumber(), 34) + " | " + clean(r.getRequestType(), 45) + " | " + clean(r.getStatus(), 25));
            case RECOGNITION -> format("Your recognition nominations", recognition.findByNomineeUserIdOrNominatedByUserIdOrderByCreatedAtDesc(id, id),
                    r -> clean(r.getRecognitionNumber(), 34) + " | " + clean(r.getAwardType(), 45) + " | " + clean(r.getStatus(), 25));
            case SUGGESTION -> format("Your employee suggestions", suggestions.findByUserIdOrderByCreatedAtDesc(id),
                    r -> clean(r.getSuggestionNumber(), 34) + " | " + clean(r.getCategory(), 45) + " | " + clean(r.getStatus(), 25));
            case NONE -> null;
        };
    }

    private static String label(Topic topic) {
        return switch (topic) {
            case HR_REQUEST -> "HR service requests";
            case TRAVEL -> "travel requests";
            case OVERTIME -> "overtime requests";
            case DATA_CHANGE -> "profile change requests";
            case LETTER -> "letter requests";
            case DOCUMENT -> "documents";
            case ATTENDANCE_CORRECTION -> "attendance corrections";
            case ACCESS_CARD -> "access-card requests";
            case RECOGNITION -> "recognition nominations";
            case SUGGESTION -> "suggestions";
            case NONE -> "records";
        };
    }

    private static <T> String format(String title, List<T> rows, java.util.function.Function<T, String> line) {
        if (rows == null || rows.isEmpty()) return "No records found for " + title.toLowerCase(Locale.ROOT) + ".";
        return title + " (" + rows.size() + " total; newest 8):\n"
                + rows.stream().limit(8).map(item -> "- " + line.apply(item)).collect(Collectors.joining("\n"))
                + "\nSource: authenticated, read-only portal records. Open the corresponding portal module for details or actions."
                + " The assistant cannot approve or edit these records from chat.";
    }

    private static String date(Object d) { return d == null ? "not recorded" : d.toString(); }
    private static String clean(String input, int length) {
        if (input == null || input.isBlank()) return "not recorded";
        String safe = input.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return safe.length() > length ? safe.substring(0, length) + "..." : safe;
    }
}
