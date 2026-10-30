package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.AiActionPreview;
import EmployeeManagementSystem.repository.AiActionPreviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiActionTokenStoreTest {
    private final AiActionPreviewRepository repo = mock(AiActionPreviewRepository.class);
    private final AiActionTokenStore store = new AiActionTokenStore(repo, new ObjectMapper());
    private static final String TOKEN = "AbCdEf0123456789AbCdEf0123456789";

    private static AiActionPreviewRepository.TokenSummaryRow summary(String type, String state,
            Instant expiry, Instant committedAt, String reference) {
        var row = mock(AiActionPreviewRepository.TokenSummaryRow.class);
        when(row.getActionType()).thenReturn(type);
        when(row.getState()).thenReturn(state);
        when(row.getExpiresAt()).thenReturn(expiry);
        when(row.getCommittedAt()).thenReturn(committedAt);
        when(row.getBusinessReference()).thenReturn(reference);
        return row;
    }

    @Test void statusIsProjectionOnlyAndNeverHydratesPendingDraft() {
        var expiry = Instant.now().plusSeconds(100);
        var projected = summary("SUPPORT_TICKET", "PREPARED", expiry, null, null);
        when(repo.findTokenSummaryForAccount(AiActionTokenStore.digest(TOKEN), "employee1", "ROLE_EMPLOYEE"))
                .thenReturn(Optional.of(projected));
        assertEquals("PREPARED", store.status(TOKEN, "employee1", "ROLE_EMPLOYEE").state());
        assertNull(store.status(TOKEN, "employee2", "ROLE_EMPLOYEE"));
        verify(repo, never()).findByTokenHashAndActorIdAndActorRole(anyString(), anyString(), anyString());
        verify(repo, never()).findById(anyString());
    }

    @Test void storesOnlyDigestAndRestoresOriginalValidatedFields() {
        var expiry = Instant.now().plusSeconds(600);
        store.issue(TOKEN, "employee1", "ROLE_EMPLOYEE", "SUPPORT_TICKET",
                Map.of("subject", "Laptop repair", "description", "Please repair this laptop"), expiry, null);
        ArgumentCaptor<AiActionPreview> row = ArgumentCaptor.forClass(AiActionPreview.class);
        verify(repo).saveAndFlush(row.capture());
        assertEquals("employee1", row.getValue().getActorId());
        assertEquals(64, AiActionTokenStore.digest(TOKEN).length());
        assertNotEquals(TOKEN, AiActionTokenStore.digest(TOKEN));
        when(repo.findByTokenHashAndActorIdAndActorRole(AiActionTokenStore.digest(TOKEN), "employee1", "ROLE_EMPLOYEE")).thenReturn(Optional.of(row.getValue()));
        assertEquals("Laptop repair", store.get(TOKEN, "employee1", "ROLE_EMPLOYEE").fields().get("subject"));
        assertEquals("ROLE_EMPLOYEE", store.get(TOKEN, "employee1", "ROLE_EMPLOYEE").role());
        var projected = summary("SUPPORT_TICKET", "PREPARED", expiry, null, null);
        when(repo.findTokenSummaryForAccount(AiActionTokenStore.digest(TOKEN), "employee1", "ROLE_EMPLOYEE"))
                .thenReturn(Optional.of(projected));
        assertNull(store.status(TOKEN, "employee1", "ROLE_EMPLOYEE").reference());
    }

    @Test void otherAccountsNeverLoadOrParseForeignDraftDetails() {
        // Only the authenticated account reaches the repository query; a mismatched
        // owner or current role never fetches the stored payload in the first place.
        assertNull(store.get(TOKEN, "employee2", "ROLE_EMPLOYEE"));
        assertNull(store.get(TOKEN, "employee1", "ROLE_ADMIN"));
        assertNull(store.status(TOKEN, "employee2", "ROLE_EMPLOYEE"));
        verify(repo).findByTokenHashAndActorIdAndActorRole(
                AiActionTokenStore.digest(TOKEN), "employee2", "ROLE_EMPLOYEE");
        verify(repo).findByTokenHashAndActorIdAndActorRole(
                AiActionTokenStore.digest(TOKEN), "employee1", "ROLE_ADMIN");
        verify(repo).findTokenSummaryForAccount(
                AiActionTokenStore.digest(TOKEN), "employee2", "ROLE_EMPLOYEE");
        verify(repo, never()).findById(anyString());
    }

    @Test void databaseClaimAndCancellationRequireActorAndRole() {
        when(repo.consumeOnce(anyString(), anyString(), anyString(), any())).thenReturn(1, 0);
        assertTrue(store.consume(TOKEN, "employee1", "ROLE_EMPLOYEE"));
        assertFalse(store.consume(TOKEN, "employee1", "ROLE_EMPLOYEE"));
        verify(repo, times(2)).consumeOnce(eq(AiActionTokenStore.digest(TOKEN)), eq("employee1"),
                eq("ROLE_EMPLOYEE"), any());
        when(repo.cancelOnce(anyString(), anyString(), anyString(), any())).thenReturn(0);
        assertFalse(store.cancel(TOKEN, "employee2", "ROLE_EMPLOYEE"));
    }

    @Test void statusExposesOnlyStatesToTheOriginalAccount() {
        var expiry = Instant.now().plusSeconds(600);
        var row = new AiActionPreview(AiActionTokenStore.digest(TOKEN), "employee1", "ROLE_EMPLOYEE",
                "LEAVE_REQUEST", "{\"reason\":\"private\"}", null, expiry);
        var projected = summary("LEAVE_REQUEST", "PREPARED", expiry, null, null);
        when(repo.findTokenSummaryForAccount(AiActionTokenStore.digest(TOKEN), "employee1", "ROLE_EMPLOYEE"))
                .thenReturn(Optional.of(projected));
        assertEquals("PREPARED", store.status(TOKEN, "employee1", "ROLE_EMPLOYEE").state());
        assertNull(store.status(TOKEN, "employee2", "ROLE_EMPLOYEE"));
        assertNull(store.status(TOKEN, "employee1", "ROLE_ADMIN"));
    }

    @Test void monitoringCountsExcludeUserDataAndTrackExpiredDrafts() {
        when(repo.countByStateAndExpiresAtAfterAndCreatedAtAfter(eq("PREPARED"), any(), any())).thenReturn(2L);
        when(repo.countByStateAndExpiresAtLessThanEqualAndCreatedAtAfter(eq("PREPARED"), any(), any())).thenReturn(3L);
        when(repo.countByStateAndCreatedAtAfter(eq("CONSUMED"), any())).thenReturn(4L);
        when(repo.countByStateAndCreatedAtAfter(eq("CANCELLED"), any())).thenReturn(5L);
        when(repo.countByCommittedAtAfter(any())).thenReturn(2L);
        var counts = store.monitor(Instant.now().minusSeconds(86400), Instant.now());
        assertEquals(2L, counts.active()); assertEquals(3L, counts.expired());
        assertEquals(4L, counts.consumed()); assertEquals(5L, counts.cancelled());
        assertEquals(2L, counts.recordedCommits());
    }

    @Test void completionReceiptRecordsOnlyOneCommittedOutcome() {
        when(repo.recordCommit(anyString(), eq("employee1"), eq("ROLE_EMPLOYEE"), eq("INC-102"), any()))
            .thenReturn(1, 0);
        store.recordCommit(TOKEN, "employee1", "ROLE_EMPLOYEE", "INC-102");
        assertThrows(IllegalStateException.class,
            () -> store.recordCommit(TOKEN, "employee1", "ROLE_EMPLOYEE", "INC-102"));
        verify(repo, times(2)).recordCommit(eq(AiActionTokenStore.digest(TOKEN)),
            eq("employee1"), eq("ROLE_EMPLOYEE"), eq("INC-102"), any());
    }

    @Test void completionReceiptNeverRevealsBusinessPayloadOrForeignReference() throws Exception {
        var row = new AiActionPreview(AiActionTokenStore.digest(TOKEN), "employee1", "ROLE_EMPLOYEE",
                "SUPPORT_TICKET", "{\"description\":\"private\"}", null, Instant.now().plusSeconds(60));
        var statusField = AiActionPreview.class.getDeclaredField("state");
        statusField.setAccessible(true); statusField.set(row, "CONSUMED");
        var committedField = AiActionPreview.class.getDeclaredField("committedAt");
        committedField.setAccessible(true); committedField.set(row, Instant.now());
        var refField = AiActionPreview.class.getDeclaredField("businessReference");
        refField.setAccessible(true); refField.set(row, "INC-103");
        var projected = summary("SUPPORT_TICKET", "CONSUMED", Instant.now().plusSeconds(60), Instant.now(), "INC-103");
        when(repo.findTokenSummaryForAccount(AiActionTokenStore.digest(TOKEN), "employee1", "ROLE_EMPLOYEE"))
                .thenReturn(Optional.of(projected));
        var status = store.status(TOKEN, "employee1", "ROLE_EMPLOYEE");
        assertEquals("COMMITTED", status.state());
        assertEquals("INC-103", status.reference());
        assertNull(store.status(TOKEN, "employee2", "ROLE_EMPLOYEE"));
    }

    @Test void rejectsMalformedTokensAndFailClosedExpiredLookup() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> store.get("short", "employee1", "ROLE_EMPLOYEE"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> store.consume("bad/token", "a", "b"));
        when(repo.findByTokenHashAndActorIdAndActorRole(AiActionTokenStore.digest(TOKEN), "employee1", "ROLE_EMPLOYEE")).thenReturn(Optional.empty());
        assertNull(store.get(TOKEN, "employee1", "ROLE_EMPLOYEE"));
    }
    @Test void recentRecoveryHistoryUsesAccountRoleScopedSummaryRowsOnly() {
        Instant now = Instant.now();
        var committed = mock(AiActionPreviewRepository.RecentActionRow.class);
        when(committed.getActionType()).thenReturn("SUPPORT_TICKET");
        when(committed.getState()).thenReturn("CONSUMED");
        when(committed.getCreatedAt()).thenReturn(now.minusSeconds(20));
        when(committed.getExpiresAt()).thenReturn(now.plusSeconds(60));
        when(committed.getCommittedAt()).thenReturn(now);
        when(committed.getBusinessReference()).thenReturn("INC-2026");
        var uncertain = mock(AiActionPreviewRepository.RecentActionRow.class);
        when(uncertain.getActionType()).thenReturn("EXPENSE_CLAIM");
        when(uncertain.getState()).thenReturn("CONSUMED");
        when(uncertain.getCreatedAt()).thenReturn(now.minusSeconds(30));
        when(uncertain.getExpiresAt()).thenReturn(now.plusSeconds(60));
        var expired = mock(AiActionPreviewRepository.RecentActionRow.class);
        when(expired.getActionType()).thenReturn("LEAVE_REQUEST");
        when(expired.getState()).thenReturn("PREPARED");
        when(expired.getCreatedAt()).thenReturn(now.minusSeconds(40));
        when(expired.getExpiresAt()).thenReturn(now.minusSeconds(60));

        when(repo.findRecentActionSummaries(eq("employee1"), eq("ROLE_EMPLOYEE"), any(), any()))
                .thenReturn(java.util.List.of(committed, uncertain, expired));
        var results = store.recentForAccount("employee1", "ROLE_EMPLOYEE");
        assertEquals(3, results.size());
        assertEquals("COMMITTED", results.get(0).state());
        assertEquals("INC-2026", results.get(0).reference());
        assertEquals("CONSUMED", results.get(1).state());
        assertNull(results.get(1).reference());
        assertEquals("EXPIRED", results.get(2).state());
        assertEquals(java.util.Set.of("type", "state", "createdAt", "reference"),
                java.util.Arrays.stream(AiActionTokenStore.RecentAction.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet()));
        verify(repo).findRecentActionSummaries(eq("employee1"), eq("ROLE_EMPLOYEE"),
                argThat(since -> since.isBefore(Instant.now()) && since.isAfter(Instant.now().minusSeconds(86430))),
                argThat(page -> page.getPageNumber() == 0 && page.getPageSize() == 12));
        verify(repo, never()).findAll();
        verify(uncertain, never()).getBusinessReference();
    }

    @org.junit.jupiter.api.Test
    void receiptWriteMustParticipateInCallerTransaction() throws Exception {
        var method = AiActionTokenStore.class.getMethod("recordCommit",
                String.class, String.class, String.class, String.class);
        var annotation = method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        org.junit.jupiter.api.Assertions.assertNotNull(annotation);
        org.junit.jupiter.api.Assertions.assertEquals(org.springframework.transaction.annotation.Propagation.MANDATORY,
                annotation.propagation());
    }

    @Test void periodicCleanupRedactsClosedAndExpiredRowsBeforePurgingLedger() {
        when(repo.redactClosedOrExpired(any())).thenReturn(4);
        store.cleanupExpired();
        var ordered = inOrder(repo);
        ordered.verify(repo).redactClosedOrExpired(any(Instant.class));
        ordered.verify(repo).purgeExpired(any(Instant.class));
    }

}
