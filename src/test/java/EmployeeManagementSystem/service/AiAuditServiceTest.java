package EmployeeManagementSystem.service;
import EmployeeManagementSystem.repository.AiAuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiAuditServiceTest {
    @Test void commitFailureNeverReplacesTheApplicationResult() {
        var repo=mock(AiAuditRepository.class); var manager=mock(PlatformTransactionManager.class);
        var status=new SimpleTransactionStatus(); when(manager.getTransaction(any())).thenReturn(status);
        doThrow(new UnexpectedRollbackException("commit failed")).when(manager).commit(status);
        var service=new AiAuditService(repo,manager);
        assertDoesNotThrow(() -> service.record("CHAT","DENIED"));
        verify(repo).saveAndFlush(any()); verify(manager).commit(status);
    }
    @Test void flushFailureRollsBackWithoutEscaping() {
        var repo=mock(AiAuditRepository.class); var manager=mock(PlatformTransactionManager.class);
        var status=new SimpleTransactionStatus(); when(manager.getTransaction(any())).thenReturn(status);
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("flush failed"));
        assertDoesNotThrow(() -> new AiAuditService(repo,manager).record("CHAT","SUCCESS"));
        verify(manager).rollback(status);
    }
}
