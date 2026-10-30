package EmployeeManagementSystem.service;

import EmployeeManagementSystem.dto.EmployeeDocumentDTO;
import EmployeeManagementSystem.entity.EmployeeDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** On-demand, extractive search in the authenticated employee's OWN uploaded PDF/DOCX files.
 *  No document text is sent to an LLM or indexed into a shared search collection. */
@Service
public class AiOwnedDocumentSearch {
    private static final Set<String> STOP = Set.of("search", "find", "look", "inside", "within", "uploaded", "my", "own", "document", "documents", "file", "files", "pdf", "docx", "for", "about", "show", "me", "which", "contains", "contain", "the", "that", "what", "where", "and", "from", "is", "does", "text", "of", "in", "all", "a", "an", "to", "please");
    private static final int MAX_FILES = 12, MAX_BYTES = 4 * 1024 * 1024, MAX_TEXT = 90000;
    private final EmployeeDocumentService documents;

    public AiOwnedDocumentSearch(EmployeeDocumentService documents) { this.documents = documents; }

    public static boolean handles(String q) {
        if (q == null) return false;
        String s = " " + q.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
        boolean owned = s.contains(" my ") || s.contains(" own ");
        boolean content = s.contains(" search ") || s.contains(" find ") || s.contains(" inside ") || s.contains(" contents ") || s.contains(" contains ");
        boolean files = s.contains(" document ") || s.contains(" documents ") || s.contains(" file ") || s.contains(" files ") || s.contains(" pdf ") || s.contains(" docx ");
        return owned && content && files;
    }

    public String answer(String question, String verifiedAccount) {
        if (verifiedAccount == null || verifiedAccount.isBlank()) throw new AccessDeniedException("Verified employee required");
        List<String> terms = Arrays.stream(question.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(s -> s.length() >= 3 && !STOP.contains(s)).distinct().limit(10).toList();
        if (terms.isEmpty()) return "Specify a topic or phrase to find in your uploaded documents. Example: search my uploaded documents for training.";
        // service.list(false) is account-scoped; getOwned(...) checks ownership again before reading bytes.
        List<EmployeeDocumentDTO> mine = documents.list(verifiedAccount, false);
        int inspected = 0, unsupported = 0;
        List<String> matches = new ArrayList<>();
        for (EmployeeDocumentDTO dto : mine) {
            if (inspected >= MAX_FILES || matches.size() >= 5) break;
            String filename = dto.originalFilename() == null ? "" : dto.originalFilename().toLowerCase(Locale.ROOT);
            if (!filename.endsWith(".pdf") && !filename.endsWith(".docx")) { unsupported++; continue; }
            if (dto.fileSize() == null || dto.fileSize() <= 0 || dto.fileSize() > MAX_BYTES) { unsupported++; continue; }
            inspected++;
            try {
                EmployeeDocument document = documents.getOwned(dto.id(), verifiedAccount, false);
                byte[] data = documents.bytes(document);
                if (data.length > MAX_BYTES) { unsupported++; continue; }
                String text = filename.endsWith(".pdf") ? pdf(data) : docx(data);
                String excerpt = findExcerpt(text, terms);
                if (excerpt != null) matches.add("- Document #" + dto.id() + " (" + safe(dto.originalFilename(), 80) + "): " + excerpt);
            } catch (IOException ex) {
                // Broken/unsupported file must not expose storage paths or stop other authorized results.
                unsupported++;
            } catch (RuntimeException ex) {
                // Preserve access-control failures; malformed Office/PDF inputs may also throw runtime errors.
                if (ex instanceof AccessDeniedException) throw ex;
                unsupported++;
            }
        }
        if (matches.isEmpty()) return "No matching readable text was found in up to " + inspected
                + " of your most recent PDF/DOCX uploads. Scanned images, old .doc files and large files may not be searchable. "
                + "Open My Documents to check the original.";
        return "Matches in your own uploaded files (extractive excerpts; not AI-generated):\n\n"
                + String.join("\n\n", matches)
                + "\n\nSource: My Documents. Open each document by ID to verify the full context. "
                + "Searched " + inspected + " recent readable files; skipped " + unsupported + " unsupported/oversized files.";
    }

    static String findExcerpt(String text, List<String> terms) {
        if (text == null || text.isBlank()) return null;
        String normalized = text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").replaceAll("\\s+", " ").trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        int best = -1;
        for (String term : terms) {
            var matcher = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(term) + "(?![a-z0-9])").matcher(lower);
            if (matcher.find() && (best < 0 || matcher.start() < best)) best = matcher.start();
        }
        if (best < 0) return null;
        int start = Math.max(0, best - 100), end = Math.min(normalized.length(), best + 340);
        return (start > 0 ? "…" : "") + safe(normalized.substring(start, end), 450) + (end < normalized.length() ? "…" : "");
    }

    private static String pdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(12, doc.getNumberOfPages()));
            return limit(stripper.getText(doc));
        }
    }
    private static String docx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder out = new StringBuilder();
            for (var p : doc.getParagraphs()) {
                if (out.length() >= MAX_TEXT) break;
                out.append(p.getText()).append(' ');
            }
            for (var table : doc.getTables()) {
                if (out.length() >= MAX_TEXT) break;
                for (var row : table.getRows()) for (var cell : row.getTableCells()) {
                    if (out.length() < MAX_TEXT) out.append(cell.getText()).append(' ');
                }
            }
            return limit(out.toString());
        }
    }
    private static String limit(String s) { return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT); }
    private static String safe(String s, int max) {
        String cleaned = s.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + "…";
    }
}
