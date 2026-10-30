package EmployeeManagementSystem.service;

import EmployeeManagementSystem.repository.AiChatEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Pageable;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiHistoryServiceTest {
  @AfterEach void reset(){SecurityContextHolder.clearContext();}
  @Test void unauthenticatedHistoryIsRejected(){
    AiHistoryService service=new AiHistoryService(mock(AiChatEntryRepository.class));
    assertThrows(AccessDeniedException.class,()->service.recent(30));
  }
  @Test void historyIsScopedToSignedInUser(){
    AiChatEntryRepository repo=mock(AiChatEntryRepository.class);
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("EMP0003","n/a",List.of()));
    new AiHistoryService(repo).recent(30);
    verify(repo).findByOwnerIdOrderByCreatedAtDesc(eq("EMP0003"),any(Pageable.class));
    verify(repo,never()).findAll();
  }
  @Test void historyLookupIsScopedByOwner(){
    AiChatEntryRepository repo=mock(AiChatEntryRepository.class);
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("EMP0003","n/a",List.of()));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new AiHistoryService(repo).get(7L));
    verify(repo).findByIdAndOwnerId(7L,"EMP0003");
  }

  @Test void savedQuestionAndAnswerAreBounded(){
    AiChatEntryRepository repo=mock(AiChatEntryRepository.class);
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("EMP0003","n/a",List.of()));
    new AiHistoryService(repo).save(" My leave status ", "x".repeat(25000));
    var capture=org.mockito.ArgumentCaptor.forClass(EmployeeManagementSystem.entity.AiChatEntry.class);
    verify(repo).save(capture.capture());
    assertEquals("My leave status",capture.getValue().getQuestion());
    assertEquals(16000,capture.getValue().getAnswer().length());
    assertEquals("EMP0003",capture.getValue().getOwnerId());
  }
  @Test void staleSingleHistoryRecordIsUnavailable(){
    AiChatEntryRepository repo=mock(AiChatEntryRepository.class);
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("EMP0003","n/a",List.of()));
    var entry=new EmployeeManagementSystem.entity.AiChatEntry("EMP0003","old message","old answer");
    try {
      var date=EmployeeManagementSystem.entity.AiChatEntry.class.getDeclaredField("createdAt");
      date.setAccessible(true); date.set(entry,java.time.Instant.now().minusSeconds(100L*86400));
    } catch (ReflectiveOperationException ex) {throw new AssertionError(ex);}
    when(repo.findByIdAndOwnerId(7L,"EMP0003")).thenReturn(java.util.Optional.of(entry));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new AiHistoryService(repo).get(7L));
  }
  @Test void scheduledPurgeDeletesOnlyOldChatEntries(){
    AiChatEntryRepository repo=mock(AiChatEntryRepository.class);
    new AiHistoryService(repo).purgeExpiredHistory();
    verify(repo).deleteByCreatedAtBefore(any(java.time.Instant.class));
    verify(repo,never()).deleteAll();
  }

}
