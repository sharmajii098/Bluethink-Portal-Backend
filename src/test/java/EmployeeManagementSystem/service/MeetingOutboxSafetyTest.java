package EmployeeManagementSystem.service;
import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MeetingOutboxSafetyTest {
 MeetingReminderRepository rows;PortalMeetingRepository meetings;MeetingOutboxTransactions service;MeetingReminder row;PortalMeeting meeting;
 @BeforeEach void setup(){rows=mock(MeetingReminderRepository.class);meetings=mock(PortalMeetingRepository.class);service=new MeetingOutboxTransactions(rows,meetings);meeting=new PortalMeeting();meeting.id=1L;meeting.title="Test meeting";meeting.startsAt=OffsetDateTime.now().plusHours(2);meeting.endsAt=meeting.startsAt.plusMinutes(30);meeting.calendarSequence=2;row=new MeetingReminder();row.id=10L;row.meeting=meeting;row.meetingSequence=2;row.sendAt=OffsetDateTime.now().minusMinutes(1);when(rows.findById(10L)).thenReturn(Optional.of(row));when(rows.lock(10L)).thenReturn(Optional.of(row));when(meetings.lock(1L)).thenReturn(Optional.of(meeting));}
 @Test void secondClaimIsIgnored(){var first=service.claim(10L);assertNotNull(first);assertEquals("SENDING",row.status);assertNull(service.claim(10L));assertEquals(1,row.attempts);}
 @Test void staleSequenceIsCancelledBeforeSending(){row.meetingSequence=1;assertNull(service.claim(10L));assertEquals("CANCELLED",row.status);}
 @Test void cancelledMeetingCannotSendOldInvitation(){meeting.status="CANCELLED";row.kind="INVITE";assertNull(service.claim(10L));assertEquals("CANCELLED",row.status);}
 @Test void cancellationNoticeCanBeSent(){meeting.status="CANCELLED";row.kind="CANCEL";assertNotNull(service.claim(10L));}
 @Test void acceptedMessageIsNotAutomaticallyResent(){var claim=service.claim(10L);service.finish(claim,"SENT");assertEquals("SENT",row.status);assertNotNull(row.sentAt);assertNull(service.claim(10L));}
 @Test void uncertainDeliveryRequiresReview(){var claim=service.claim(10L);service.finish(claim,"DELIVERY_UNKNOWN");assertNull(service.claim(10L));assertEquals("DELIVERY_UNKNOWN",row.status);}
 @Test void crashedClaimIsQuarantined(){service.claim(10L);row.claimedAt=OffsetDateTime.now().minusMinutes(11);service.quarantine(10L);assertEquals("DELIVERY_UNKNOWN",row.status);}
 @Test void failedConnectionHasFiniteRetries(){row.attempts=2;var claim=service.claim(10L);service.finish(claim,"RETRY");assertEquals("FAILED",row.status);}
 @Test void replacementClaimCannotBeFinalizedByOldWorker(){var claim=service.claim(10L);row.claimToken="another-worker";service.finish(claim,"SENT");assertEquals("SENDING",row.status);}
}
