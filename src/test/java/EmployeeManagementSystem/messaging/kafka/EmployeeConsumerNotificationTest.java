package EmployeeManagementSystem.messaging.kafka;

import EmployeeManagementSystem.messaging.kafka.EmployeeEvent;
import EmployeeManagementSystem.service.EmailTemplates;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmployeeConsumerNotificationTest {

    @Test
    void preparesWelcomeEmailForSyntheticEmployee() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmployeeConsumer consumer = new EmployeeConsumer(sender);

        EmployeeEvent event = new EmployeeEvent(
            90000001L,
            "Synthetic Employee",
            "synthetic@example.invalid"
        );

        try (MockedStatic<EmailTemplates> emails =
                 mockStatic(EmailTemplates.class)) {

            consumer.consumeEmployeeCreatedEvent(event);

            ArgumentCaptor<SimpleMailMessage> captured =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

            emails.verify(() ->
                EmailTemplates.send(same(sender), captured.capture())
            );

            SimpleMailMessage message = captured.getValue();

            assertArrayEquals(
                new String[]{"synthetic@example.invalid"},
                message.getTo()
            );

            assertEquals(
                "Welcome to Employee Management System",
                message.getSubject()
            );

            assertTrue(
                message.getText().contains("Synthetic Employee")
            );
        }
    }

    @Test
    void smtpFailurePropagatesForKafkaRetry() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmployeeConsumer consumer = new EmployeeConsumer(sender);

        EmployeeEvent event = new EmployeeEvent(
            90000002L,
            "Synthetic Employee",
            "synthetic@example.invalid"
        );

        try (MockedStatic<EmailTemplates> emails =
                 mockStatic(EmailTemplates.class)) {

            emails.when(() ->
                EmailTemplates.send(
                    same(sender),
                    any(SimpleMailMessage.class)
                )
            ).thenThrow(
                new IllegalStateException("Simulated SMTP failure")
            );

            assertThrows(
                RuntimeException.class,
                () -> consumer.consumeEmployeeCreatedEvent(event)
            );
        }
    }
}