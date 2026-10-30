package EmployeeManagementSystem.service;

import EmployeeManagementSystem.repository.MeetingReminderRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

@Component
public class MeetingOutboxWorker {
    private final MeetingReminderRepository rows;private final MeetingOutboxTransactions transactions;
    private final ObjectProvider<JavaMailSender> provider;private final boolean enabled;private final String from;
    public MeetingOutboxWorker(MeetingReminderRepository rows,MeetingOutboxTransactions transactions,ObjectProvider<JavaMailSender> provider,
          @Value("${bluethink.meetings.email.enabled:false}") boolean enabled,@Value("${bluethink.meetings.email.from:}") String from){
        this.rows=rows;this.transactions=transactions;this.provider=provider;this.enabled=enabled;this.from=from;
        if(enabled)MeetingRecipients.normalize(from);
    }
    @Scheduled(fixedDelayString="${bluethink.meetings.reminders.poll-ms:60000}")
    public void poll(){
        for(Long id:rows.stale(OffsetDateTime.now().minusMinutes(10),PageRequest.of(0,50)))try{transactions.quarantine(id);}catch(RuntimeException ignored){/* a competing worker may own this row */}
        if(!enabled)return;JavaMailSender sender=provider.getIfAvailable();if(sender==null)return;
        for(Long id:rows.due(OffsetDateTime.now(),PageRequest.of(0,30))){
            MeetingOutboxTransactions.Claim c;try{c=transactions.claim(id);}catch(RuntimeException ignored){continue;}if(c==null)continue;
            String outcome="DELIVERY_UNKNOWN";
            try{
                MimeMessage message=sender.createMimeMessage();MimeMessageHelper helper=new MimeMessageHelper(message,true,"UTF-8");
                helper.setFrom(from);helper.setTo(c.recipient());helper.setSubject("BlueThink "+c.kind().toLowerCase()+": "+c.title().replaceAll("[\\r\\n]"," "));
                String plain="Meeting: "+c.title()+"\nStart: "+c.start()+"\nEnd: "+c.end()+"\nLink: "+c.url()+"\nAgenda: "+(c.agenda()==null?"":c.agenda())+"\nStatus: "+("CANCEL".equals(c.kind())?"CANCELLED":"SCHEDULED");
                helper.setText(plain,EmailTemplates.html("Meeting "+c.kind().toLowerCase(),plain,null,null));
                String calendar=MeetingCalendarText.build(c.meetingId(),c.title(),c.agenda(),c.url(),c.start(),c.end(),c.sequence(),"CANCEL".equals(c.kind()),from,c.recipient());
                helper.addAttachment("bluethink-meeting.ics",new ByteArrayResource(calendar.getBytes(StandardCharsets.UTF_8)),"text/calendar; charset=UTF-8; method="+("CANCEL".equals(c.kind())?"CANCEL":"REQUEST"));
                message.setHeader("X-BlueThink-Delivery",String.valueOf(c.id()));sender.send(message);outcome="SENT";
            }catch(MailAuthenticationException e){outcome="RETRY";}
            catch(MailSendException e){
                // A connection failure before any message was attempted is retryable. Send/timeout errors are ambiguous.
                if(e.getFailedMessages().isEmpty() && e.getCause() instanceof java.net.ConnectException)outcome="RETRY";
            }catch(Exception e){/* Keep uncertain sends out of automatic retry; never leak SMTP internals. */}
            try{transactions.finish(c,outcome);}catch(RuntimeException ignored){/* quarantine handles an unrecorded outcome; do not resend */}
        }
    }
}
