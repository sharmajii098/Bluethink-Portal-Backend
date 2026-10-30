package EmployeeManagementSystem.service;

import EmployeeManagementSystem.repository.AccountInvitationRepository;
import EmployeeManagementSystem.entity.AccountInvitation;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountInvitationSecurityTest {

    private static final String TOKEN = "A".repeat(43);

    private AccountInvitationRepository repository() {
        return mock(AccountInvitationRepository.class);
    }

    private AccountInvitationService service(
            AccountInvitationRepository repository) {

        AccountInvitationService service = mock(
            AccountInvitationService.class,
            CALLS_REAL_METHODS
        );

        ReflectionTestUtils.setField(
            service, "invitations", repository
        );

        return service;
    }

    private AccountInvitation invitation(
            Instant expiry, Instant consumedAt) {

        AccountInvitation invitation =
            mock(AccountInvitation.class);

        when(invitation.getExpiresAt()).thenReturn(expiry);

        when(invitation.getConsumedAt())
            .thenReturn(consumedAt);

        return invitation;
    }

    @Test
    void rejectsInvalidTokenFormat() {
        AccountInvitationRepository repo = repository();
        AccountInvitationService service = service(repo);

        assertFalse(service.valid(null));
        assertFalse(service.valid(""));
        assertFalse(service.valid("invalid-token"));

        verifyNoInteractions(repo);
    }

    @Test
    void rejectsUnknownToken() {
        AccountInvitationRepository repo = repository();

        when(repo.findByTokenHash(anyString()))
            .thenReturn(Optional.empty());

        assertFalse(service(repo).valid(TOKEN));
    }

    @Test
    void rejectsExpiredToken() {
        AccountInvitationRepository repo = repository();

        AccountInvitation expired = invitation(
            Instant.now().minusSeconds(3600),
            null
        );

        when(repo.findByTokenHash(anyString()))
            .thenReturn(Optional.of(expired));

        assertFalse(service(repo).valid(TOKEN));
    }

    @Test
    void rejectsAlreadyConsumedToken() {
        AccountInvitationRepository repo = repository();

        AccountInvitation consumed = invitation(
            Instant.now().plusSeconds(3600),
            Instant.now().minusSeconds(60)
        );

        when(repo.findByTokenHash(anyString()))
            .thenReturn(Optional.of(consumed));

        assertFalse(service(repo).valid(TOKEN));
    }

    @Test
    void acceptsUnusedUnexpiredToken() {
        AccountInvitationRepository repo = repository();

        AccountInvitation active = invitation(
            Instant.now().plusSeconds(3600),
            null
        );

        when(repo.findByTokenHash(anyString()))
            .thenReturn(Optional.of(active));

        assertTrue(service(repo).valid(TOKEN));
    }

    @Test
    void rejectsActivationWhenAtomicConsumptionFails() {
        AccountInvitationRepository repo = repository();

        AccountInvitation active = invitation(
            Instant.now().plusSeconds(3600),
            null
        );

        when(repo.findByTokenHash(anyString()))
            .thenReturn(Optional.of(active));

        // Mockito returns zero by default for consumeIfValid().
        // This simulates a token already consumed by another request.
        assertThrows(
            ResponseStatusException.class,
            () -> service(repo).activate(
                TOKEN, "SecurePassword123!"
            )
        );

        verify(repo).consumeIfValid(
            nullable(Long.class),
            any(Instant.class)
        );
    }
}