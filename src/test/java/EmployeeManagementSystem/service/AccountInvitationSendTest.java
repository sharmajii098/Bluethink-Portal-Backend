package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.RegisterEmployee;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountInvitationSendTest {

    private AccountInvitationService service(
            JavaMailSender sender, String mailFrom) {

        AccountInvitationService service = mock(
            AccountInvitationService.class,
            CALLS_REAL_METHODS
        );

        ReflectionTestUtils.setField(service, "sender", sender);
        ReflectionTestUtils.setField(service, "mailFrom", mailFrom);
        ReflectionTestUtils.setField(
            service, "frontendUrl", "https://example.invalid"
        );

        return service;
    }

    private RegisterEmployee syntheticAccount() {
        RegisterEmployee account = mock(RegisterEmployee.class);

        when(account.getEmail())
            .thenReturn("synthetic@example.invalid");

        when(account.getName())
            .thenReturn("Synthetic Employee");

        return account;
    }

    @Test
    void preparesActivationInvitation() {
        JavaMailSender sender = mock(JavaMailSender.class);

        AccountInvitationService service = service(
            sender, "noreply@example.invalid"
        );

        RegisterEmployee account = syntheticAccount();

        try (MockedStatic<EmailTemplates> emails =
                 mockStatic(EmailTemplates.class)) {

            boolean accepted = service.send(
                account, "synthetic-activation-token"
            );

            assertTrue(accepted);

            ArgumentCaptor<SimpleMailMessage> captured =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

            emails.verify(() -> EmailTemplates.send(
                same(sender),
                captured.capture(),
                eq("Set up your account"),
                eq("https://example.invalid/activate-account?token=synthetic-activation-token")
            ));

            SimpleMailMessage message = captured.getValue();

            assertArrayEquals(
                new String[]{"synthetic@example.invalid"},
                message.getTo()
            );

            assertTrue(message.getText().contains(
                "synthetic-activation-token"
            ));
        }
    }

    @Test
    void reportsSmtpFailure() {
        JavaMailSender sender = mock(JavaMailSender.class);

        AccountInvitationService service = service(
            sender, "noreply@example.invalid"
        );

        try (MockedStatic<EmailTemplates> emails =
                 mockStatic(EmailTemplates.class)) {

            emails.when(() -> EmailTemplates.send(
                same(sender),
                any(SimpleMailMessage.class),
                anyString(),
                anyString()
            )).thenThrow(
                new IllegalStateException("Simulated SMTP failure")
            );

            assertFalse(service.send(
                syntheticAccount(), "synthetic-token"
            ));
        }
    }

    @Test
    void missingSenderPreventsInvitation() {
        JavaMailSender sender = mock(JavaMailSender.class);

        AccountInvitationService service = service(sender, "");

        assertFalse(service.send(
            syntheticAccount(), "synthetic-token"
        ));

        verifyNoInteractions(sender);
    }
}