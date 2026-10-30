package EmployeeManagementSystem.service;

import EmployeeManagementSystem.entity.*;
import EmployeeManagementSystem.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AccountInvitationService {
    private final AccountInvitationRepository invitations;
    private final RegisterEmployeeRepository accounts;
    private final UserSessionRepository sessions;
    private final PasswordResetTokenRepository resets;
    private final JavaMailSender sender;
    private final PasswordEncoder encoder;
    private final String frontendUrl;
    private final String mailFrom;
    private final SecureRandom random = new SecureRandom();

    public AccountInvitationService(AccountInvitationRepository invitations, RegisterEmployeeRepository accounts,
                                    JavaMailSender sender, PasswordEncoder encoder, UserSessionRepository sessions, PasswordResetTokenRepository resets,
                                    @Value("${portal.frontend-url:http://localhost:5173}") String frontendUrl,
                                    @Value("${spring.mail.username:}") String mailFrom) {
        this.sessions = sessions; this.resets = resets; this.invitations = invitations; this.accounts = accounts; this.sender = sender;
        this.encoder = encoder; this.frontendUrl = frontendUrl; this.mailFrom = mailFrom;
    }

    private String hash(String token) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    /** Call within the same transaction that persists the account. Raw token is never stored. */
    @Transactional
    public String issue(RegisterEmployee account) {
        for (AccountInvitation previous : invitations.findByAccountIdAndConsumedAtIsNull(account.getId())) {
            previous.setConsumedAt(Instant.now());
            invitations.save(previous);
        }
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AccountInvitation invitation = new AccountInvitation();
        invitation.setAccountId(account.getId()); invitation.setTokenHash(hash(token));
        invitation.setCreatedAt(Instant.now()); invitation.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        invitations.save(invitation);
        return token;
    }

    /** Returns only provider acceptance; SMTP acceptance does not prove inbox delivery. */
    public boolean send(RegisterEmployee account, String token) {
        if (mailFrom == null || mailFrom.isBlank()) return false;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(account.getEmail());
            message.setSubject("Bluethink HRMS | Set up your account");
            message.setText("Hello " + account.getName() + ",\n\nYour Bluethink HRMS account is ready.\n"
                + "Employee ID: " + account.getUserId() + "\n\nSet your password using this single-use link (valid for 24 hours):\n"
                + frontendUrl.replaceAll("/+$", "") + "/activate-account?token=" + token
                + "\n\nIf you did not expect this invitation, contact HR. Do not forward this link.\n\nBluethink HRMS");
            EmailTemplates.send(sender, message, "Set up your account", frontendUrl.replaceAll("/+$", "") + "/activate-account?token=" + token);
            return true;
        } catch (Exception failure) {
            // Do not print the token, email body, credentials, or SMTP secrets.
            System.err.println("Account invitation SMTP delivery failed: " + failure.getClass().getSimpleName());
            return false;
        }
    }

    public boolean valid(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return false;
        return invitations.findByTokenHash(hash(token))
            .map(inv -> inv.getConsumedAt() == null && inv.getExpiresAt().isAfter(Instant.now())).orElse(false);
    }

    @Transactional
    public void activate(String token, String password) {
        if (!PasswordResetService.validPassword(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Use at least 12 characters including a letter and number, within 72 UTF-8 bytes.");
        }
        if (!valid(token)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is invalid or expired. Ask HR to resend it.");
        AccountInvitation invitation = invitations.findByTokenHash(hash(token)).orElseThrow();
        if (invitations.consumeIfValid(invitation.getId(), Instant.now()) != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is invalid or expired. Ask HR to resend it.");
        RegisterEmployee account = accounts.findById(invitation.getAccountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account no longer exists."));
        account.setPassword(encoder.encode(password));
        accounts.save(account);
        sessions.deactivateEmployeeSessions(account.getUserId());
        revokeRecoveryLinks(account.getId());
    }
    /** Invalidate unfinished password recovery/activation links after any credential change. */
    @Transactional
    public void revokeRecoveryLinks(Long accountId) {
        Instant now = Instant.now();
        for (AccountInvitation invitation : invitations.findByAccountIdAndConsumedAtIsNull(accountId)) {
            invitation.setConsumedAt(now); invitations.save(invitation);
        }
        for (PasswordResetToken reset : resets.findByAccountIdAndConsumedAtIsNull(accountId)) {
            reset.setConsumedAt(now); resets.save(reset);
        }
    }

}
