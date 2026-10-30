package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.AiChatEntry;
import EmployeeManagementSystem.repository.AiChatEntryRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AiHistoryService {
    private final AiChatEntryRepository repo;

    @Value("${bluethink.ai.history.retention-days:90}")
    private int retentionDays = AiChatDataPolicy.DEFAULT_RETENTION_DAYS;

    public AiHistoryService(AiChatEntryRepository repo) { this.repo = repo; }

    public String owner() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || "anonymousUser".equals(auth.getName()))
            throw new AccessDeniedException("Authentication required");
        return auth.getName();
    }

    private Instant cutoff() { return AiChatDataPolicy.cutoff(Instant.now(), retentionDays); }

    @Transactional
    public void save(String question, String answer) {
        String account = owner();
        if (question != null && answer != null) {
            repo.save(new AiChatEntry(account, AiChatDataPolicy.requireQuestion(question),
                    AiChatDataPolicy.capAnswer(answer)));
        }
    }

    @Transactional(readOnly = true)
    public List<AiChatEntry> recent(int limit) {
        Instant earliest = cutoff();
        return repo.findByOwnerIdOrderByCreatedAtDesc(owner(), PageRequest.of(0, Math.max(1, Math.min(limit, 100))))
                .stream().filter(e -> AiChatDataPolicy.isRetained(e.getCreatedAt(), earliest)).toList();
    }

    @Transactional(readOnly = true)
    public AiChatEntry get(Long id) {
        AiChatEntry item = repo.findByIdAndOwnerId(id, owner())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!AiChatDataPolicy.isRetained(item.getCreatedAt(), cutoff()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return item;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<String> lastQuestion() {
        Instant earliest = cutoff();
        return repo.findFirstByOwnerIdOrderByCreatedAtDesc(owner())
                .filter(e -> AiChatDataPolicy.isRetained(e.getCreatedAt(), earliest))
                .map(AiChatEntry::getQuestion);
    }

    @Transactional
    public long clear() { return repo.deleteByOwnerId(owner()); }

    /** Housekeeping only. Deletes aged chat text, not payroll, audit, or action receipts. */
    @Scheduled(cron = "${bluethink.ai.history.purge-cron:0 15 3 * * *}")
    @Transactional
    public void purgeExpiredHistory() {
        repo.deleteByCreatedAtBefore(cutoff());
    }
}
