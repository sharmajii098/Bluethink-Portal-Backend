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

/** Email-based password recovery. Public request responses must never reveal whether an email exists. */
@Service
public class PasswordResetService {
    private static final int EXPIRY_MINUTES = 30;
    private final PasswordResetTokenRepository resets;
    private final RegisterEmployeeRepository accounts;
    private final AccountInvitationRepository invitations;
    private final UserSessionRepository sessions;
    private final JavaMailSender mailSender;
    private final PasswordEncoder encoder;
    private final String frontendUrl;
    private final String mailFrom;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(PasswordResetTokenRepository resets, RegisterEmployeeRepository accounts,
                                AccountInvitationRepository invitations, UserSessionRepository sessions,
                                JavaMailSender mailSender, PasswordEncoder encoder,
                                @Value("${portal.frontend-url:http://localhost:5173}") String frontendUrl,
                                @Value("${spring.mail.username:}") String mailFrom) {
        this.resets = resets; this.accounts = accounts; this.invitations = invitations;
        this.sessions = sessions; this.mailSender = mailSender; this.encoder = encoder;
        this.frontendUrl = frontendUrl; this.mailFrom = mailFrom;
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static boolean validTokenShape(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    public static boolean validPassword(String password) {
        return PasswordPolicy.valid(password);
    }

    /** Called through the Spring service proxy so its transaction commits BEFORE SMTP send. */
    @Transactional
    public ResetMail prepare(String providedEmail) {
        if (providedEmail == null || providedEmail.isBlank() || providedEmail.length() > 254) return null;
        RegisterEmployee account = accounts.findByEmailIgnoreCase(providedEmail.trim()).orElse(null);
        if (account == null || account.getEmail() == null || account.getEmail().isBlank()) return null;
        // Inactive accounts must be reactivated by an administrator, not by password recovery.
        if (account.getEmployee() != null && account.getEmployee().getEmployeeProfile() != null) {
            String status = account.getEmployee().getEmployeeProfile().getStatus();
            if (status != null && !"ACTIVE".equalsIgnoreCase(status)) return null;
        }
        Instant now = Instant.now();
        PasswordResetToken latest = resets.findTopByAccountIdOrderByCreatedAtDesc(account.getId()).orElse(null);
        // Cooldown and hourly limit on a per-account basis. No account-discovery response differences.
        if (latest != null && latest.getConsumedAt() == null && latest.getCreatedAt().isAfter(now.minus(60, ChronoUnit.SECONDS))) return null;
        if (resets.countByAccountIdAndCreatedAtAfter(account.getId(), now.minus(1, ChronoUnit.HOURS)) >= 5) return null;
        for (PasswordResetToken previous : resets.findByAccountIdAndConsumedAtIsNull(account.getId())) {
            previous.setConsumedAt(now);
            resets.save(previous);
        }
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PasswordResetToken entry = new PasswordResetToken();
        entry.setAccountId(account.getId());
        entry.setTokenHash(hash(token));
        entry.setCreatedAt(now);
        entry.setExpiresAt(now.plus(EXPIRY_MINUTES, ChronoUnit.MINUTES));
        PasswordResetToken saved = resets.save(entry);
        return new ResetMail(saved.getId(), account.getEmail(), account.getName(), token);
    }

    /** Never log email addresses, reset tokens, mail bodies, or SMTP credentials. */
    public void deliver(ResetMail reset) {
        if (reset == null) return;
        try {
            if (mailFrom == null || mailFrom.isBlank()) throw new IllegalStateException("SMTP sender not configured");
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(reset.email());
            message.setSubject("Bluethink HRMS | Reset your password");
            message.setText("Hello " + reset.name() + ",\n\nWe received a request to reset your Bluethink HRMS password.\n\n"
                + "Open this single-use link within 30 minutes:\n"
                + frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + reset.token()
                + "\n\nIf you did not request this change, ignore this email and contact HR if concerned."
                + "\nDo not forward this link.\n\nBluethink HRMS");
            EmailTemplates.send(mailSender, message, "Reset your password", frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + reset.token());
        } catch (Exception failure) {
            // Mark the failed link unusable. Do not leak provider details to an anonymous caller.
            resets.findById(reset.id()).ifPresent(entry -> {
                entry.setConsumedAt(Instant.now());
                resets.save(entry);
            });
            System.err.println("Password-reset email delivery failed: " + failure.getClass().getSimpleName());
        }
    }

    public boolean valid(String token) {
        if (!validTokenShape(token)) return false;
        return resets.findByTokenHash(hash(token))
            .map(entry -> entry.getConsumedAt() == null && entry.getExpiresAt().isAfter(Instant.now()))
            .orElse(false);
    }

    @Transactional
    public void reset(String token, String password) {
        if (!validPassword(password))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use at least 12 characters including a letter and number, within 72 UTF-8 bytes.");
        if (!validTokenShape(token))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset link is invalid or expired. Request a new one.");
        PasswordResetToken entry = resets.findByTokenHash(hash(token)).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset link is invalid or expired. Request a new one."));
        if (resets.consumeIfValid(entry.getId(), Instant.now()) != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset link is invalid or expired. Request a new one.");
        RegisterEmployee account = accounts.findById(entry.getAccountId()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset link is invalid or expired. Request a new one."));
        account.setPassword(encoder.encode(password));
        accounts.save(account);
        // A password change must revoke all previous authentication and unfinished activation links.
        sessions.deactivateEmployeeSessions(account.getUserId());
        for (AccountInvitation invitation : invitations.findByAccountIdAndConsumedAtIsNull(account.getId())) {
            invitation.setConsumedAt(Instant.now());
            invitations.save(invitation);
        }
        for (PasswordResetToken previous : resets.findByAccountIdAndConsumedAtIsNull(account.getId())) {
            previous.setConsumedAt(Instant.now());
            resets.save(previous);
        }
    }

    public record ResetMail(Long id, String email, String name, String token) {}
}
