package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.AiAuditEntry;
import EmployeeManagementSystem.repository.AiAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiAuditService {
    private static final Logger log = LoggerFactory.getLogger(AiAuditService.class);
    private final AiAuditRepository repo;
    private final TransactionTemplate transaction;

    public AiAuditService(AiAuditRepository repo, PlatformTransactionManager manager) {
        this.repo = repo;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Catch outside the transaction boundary so flush AND commit failures cannot
    // replace a successful response or turn a permission denial into HTTP 500.
    public void record(String event, String result) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                ? "ANONYMOUS" : auth.getName();
        try {
            transaction.executeWithoutResult(status -> repo.saveAndFlush(new AiAuditEntry(actor, event, result)));
        } catch (RuntimeException ex) {
            log.error("Failed to persist AI audit event type={}", event, ex);
        }
    }
}
