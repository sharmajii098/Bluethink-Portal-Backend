package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.Policy;
import EmployeeManagementSystem.repository.PolicyRepository;
import EmployeeManagementSystem.repository.PolicyAcknowledgementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Requires dependency-enabled Maven verify; not counted as an offline runtime test. */
@ExtendWith(MockitoExtension.class)
class PolicyServiceVisibilityTest {
    @Mock PolicyRepository policies;
    @Mock PolicyAcknowledgementRepository acknowledgements;
    @InjectMocks PolicyServiceImpl service;

    private Policy policy(long id, LocalDate date) {
        Policy result = new Policy();
        result.setId(id);
        result.setTitle("Official policy");
        result.setEffectiveDate(date);
        return result;
    }

    @Test void futureEffectivePolicyCannotBeOpenedByRegularUser() {
        when(policies.findById(7L)).thenReturn(Optional.of(policy(7L, LocalDate.now().plusDays(1))));
        assertThrows(NoSuchElementException.class, () -> service.getVisible(7L));
    }

    @Test void futureEffectivePolicyCannotBeAcknowledged() {
        when(policies.findById(7L)).thenReturn(Optional.of(policy(7L, LocalDate.now().plusDays(2))));
        assertThrows(NoSuchElementException.class, () -> service.acknowledge(7L, "EMP001"));
        verifyNoInteractions(acknowledgements);
    }

    @Test void currentlyEffectivePolicyCanBeOpened() {
        Policy record = policy(8L, LocalDate.now());
        when(policies.findById(8L)).thenReturn(Optional.of(record));
        assertSame(record, service.getVisible(8L));
    }

    @Test void substantivePolicyRevisionRevokesStaleAcknowledgements() {
        Policy previous = policy(11L, LocalDate.now().minusDays(10));
        previous.setDescription("Old approved wording");
        Policy revised = policy(11L, previous.getEffectiveDate());
        revised.setDescription("New approved wording");
        when(policies.findById(11L)).thenReturn(Optional.of(previous));
        when(policies.save(revised)).thenReturn(revised);
        assertSame(revised, service.save(revised));
        verify(acknowledgements).deleteByPolicyId(11L);
    }

    @Test void identicalPolicySaveDoesNotRevokeAcknowledgements() {
        Policy previous = policy(12L, LocalDate.now());
        previous.setDescription("Unchanged approved wording");
        Policy revised = policy(12L, previous.getEffectiveDate());
        revised.setDescription("Unchanged approved wording");
        when(policies.findById(12L)).thenReturn(Optional.of(previous));
        when(policies.save(revised)).thenReturn(revised);
        service.save(revised);
        verify(acknowledgements, never()).deleteByPolicyId(anyLong());
    }

    @Test void updateUnknownPolicyIdCannotSilentlyCreateRecord() {
        Policy incoming = policy(13L, LocalDate.now());
        when(policies.findById(13L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.save(incoming));
        verify(policies, never()).save(any());
        verifyNoInteractions(acknowledgements);
    }

    @Test void listUsesTwoBulkQueriesInsteadOfOneQueryPerPolicy() {
        Policy current = policy(8L, LocalDate.now());
        Policy future = policy(9L, LocalDate.now().plusDays(1));
        when(policies.findAllByOrderByEffectiveDateDesc()).thenReturn(List.of(future, current));
        when(acknowledgements.findUserAcknowledgementsForPolicies("EMP001", List.of(8L))).thenReturn(List.of());
        when(acknowledgements.countAcknowledgedForPolicies(List.of(8L))).thenReturn(List.of());
        var list = service.listForUser("EMP001");
        assertEquals(1, list.size());
        assertEquals(8L, list.get(0).get("id"));
        verify(acknowledgements, times(1)).findUserAcknowledgementsForPolicies("EMP001", List.of(8L));
        verify(acknowledgements, times(1)).countAcknowledgedForPolicies(List.of(8L));
        verify(acknowledgements, never()).findByPolicyIdAndUserId(anyLong(), anyString());
    }
}
