package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.AiKnowledgeChunk;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts page-tagged passages from administrator-approved PDFs. Page tags are
 * stored inside each authorized passage and do not require a schema migration.
 * No uploaded document is published automatically.
 */
final class AiKnowledgePdfPages {
    private static final Pattern PAGE_TAG = Pattern.compile("^\\[PDF page ([1-9][0-9]*)\\] ");
    private static final int MAX_PAGES = 60;
    private static final int MAX_CHARS = 60_000;

    private AiKnowledgePdfPages() {}

    static List<String> extractChunks(byte[] pdfBytes) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            if (pdf.getNumberOfPages() > MAX_PAGES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Split PDFs longer than 60 pages");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> passages = new ArrayList<>();
            int totalChars = 0;
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String content = stripper.getText(pdf);
                totalChars += content.length();
                if (totalChars > MAX_CHARS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Document exceeds 60,000 characters; split into smaller approved documents");
                }
                for (String chunk : AiKnowledgeService.chunks(content)) {
                    passages.add("[PDF page " + page + "] " + chunk);
                }
            }
            return passages;
        }
    }

    static String pageCitation(AiKnowledgeChunk chunk) {
        if (chunk == null || chunk.filename == null || chunk.content == null
                || !chunk.filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) return "";
        Matcher match = PAGE_TAG.matcher(chunk.content);
        return match.find() ? ", page " + match.group(1) : "";
    }
}
