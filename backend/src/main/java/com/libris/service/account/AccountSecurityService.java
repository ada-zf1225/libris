package com.libris.service.account;

import com.libris.domain.user.*;
import com.libris.security.TotpService;
import com.libris.service.notify.NotificationService;
import com.libris.web.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Account-security flows: password reset, email verification, TOTP MFA and
 * recovery codes. Raw tokens/codes are never stored — only SHA-256 hashes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSecurityService {

    private static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final Duration VERIFY_TTL = Duration.ofHours(24);
    private static final int RECOVERY_CODES = 10;

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totp;
    private final NotificationService notify;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final MessageSource messages;
    private final SecureRandom random = new SecureRandom();

    @Value("${libris.app-url:http://localhost:5173}")
    private String appUrl;

    // ---------- password reset ----------

    /** Always succeeds from the caller's perspective — no account enumeration. */
    @Transactional
    public void requestPasswordReset(String usernameOrEmail) {
        users.findByUsername(usernameOrEmail)
                .or(() -> users.findByEmailIgnoreCase(usernameOrEmail))
                .ifPresent(user -> {
                    if (user.getEmail() == null || user.getEmail().isBlank()) {
                        return;
                    }
                    String raw = randomToken();
                    tokens.save(new AuthToken(user.getId(), AuthToken.Purpose.RESET, sha256(raw),
                            Instant.now().plus(RESET_TTL)));
                    String link = appUrl + "/reset-password?token=" + raw;
                    sendMail(user, "mail.reset", link);
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        AuthToken token = tokens.findByTokenHashAndPurpose(sha256(rawToken), AuthToken.Purpose.RESET)
                .filter(t -> t.usable(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("error.auth.tokenInvalid"));
        User user = users.findById(token.getUserId()).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(Instant.now());
        notify.notifyUser(user.getId(), "PASSWORD_RESET", "notice.passwordReset");
    }

    // ---------- email verification ----------

    @Transactional
    public void requestEmailVerification(Long userId) {
        User user = users.findById(userId).orElseThrow();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw ApiException.badRequest("error.auth.noEmail");
        }
        if (user.isEmailVerified()) {
            return;
        }
        String raw = randomToken();
        tokens.save(new AuthToken(user.getId(), AuthToken.Purpose.VERIFY, sha256(raw),
                Instant.now().plus(VERIFY_TTL)));
        String link = appUrl + "/verify-email?token=" + raw;
        sendMail(user, "mail.verify", link);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        AuthToken token = tokens.findByTokenHashAndPurpose(sha256(rawToken), AuthToken.Purpose.VERIFY)
                .filter(t -> t.usable(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("error.auth.tokenInvalid"));
        User user = users.findById(token.getUserId()).orElseThrow();
        user.setEmailVerified(true);
        token.setUsedAt(Instant.now());
    }

    // ---------- TOTP MFA ----------

    public record TotpSetup(String secret, String otpauthUrl) {}

    /** Stages a new secret; MFA only turns on after the user confirms a valid code. */
    @Transactional
    public TotpSetup startTotpEnrollment(Long userId) {
        User user = users.findById(userId).orElseThrow();
        if (user.isMfaEnabled()) {
            throw ApiException.conflict("error.mfa.alreadyEnabled");
        }
        String secret = totp.generateSecret();
        user.setTotpSecret(secret);
        return new TotpSetup(secret, totp.otpauthUrl(secret, user.getUsername()));
    }

    @Transactional
    public List<String> confirmTotpEnrollment(Long userId, String code) {
        User user = users.findById(userId).orElseThrow();
        if (user.getTotpSecret() == null || !totp.verify(user.getTotpSecret(), code, Instant.now())) {
            throw ApiException.badRequest("error.mfa.codeInvalid");
        }
        user.setMfaEnabled(true);
        return issueRecoveryCodes(userId);
    }

    @Transactional
    public void disableTotp(Long userId, String password, String code) {
        User user = users.findById(userId).orElseThrow();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("error.auth.oldPasswordMismatch");
        }
        if (!verifyMfa(user, code)) {
            throw ApiException.badRequest("error.mfa.codeInvalid");
        }
        user.setMfaEnabled(false);
        user.setTotpSecret(null);
        recoveryCodes.deleteByUserId(userId);
    }

    /** Accepts a TOTP code or an unused recovery code (consumed on success). */
    @Transactional
    public boolean verifyMfa(User user, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        if (totp.verify(user.getTotpSecret(), code.trim(), Instant.now())) {
            return true;
        }
        String hash = sha256(code.trim().toUpperCase().replace("-", ""));
        for (MfaRecoveryCode recovery : recoveryCodes.findByUserIdAndUsedAtIsNull(user.getId())) {
            if (MessageDigest.isEqual(recovery.getCodeHash().getBytes(StandardCharsets.UTF_8),
                    hash.getBytes(StandardCharsets.UTF_8))) {
                recovery.setUsedAt(Instant.now());
                return true;
            }
        }
        return false;
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(Long userId, String code) {
        User user = users.findById(userId).orElseThrow();
        if (!user.isMfaEnabled() || !totp.verify(user.getTotpSecret(), code, Instant.now())) {
            throw ApiException.badRequest("error.mfa.codeInvalid");
        }
        return issueRecoveryCodes(userId);
    }

    private List<String> issueRecoveryCodes(Long userId) {
        recoveryCodes.deleteByUserId(userId);
        List<String> raw = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String code = randomRecoveryCode();
            raw.add(code.substring(0, 5) + "-" + code.substring(5));
            recoveryCodes.save(new MfaRecoveryCode(userId, sha256(code)));
        }
        return raw;
    }

    // ---------- helpers ----------

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String randomRecoveryCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void sendMail(User user, String key, String link) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("mail sender unavailable; {} link for user {}: {}", key, user.getId(), link);
            return;
        }
        Locale locale = Locale.forLanguageTag(user.getPreferredLocale() == null ? "zh-CN" : user.getPreferredLocale());
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("Libris <noreply@libris.local>");
            message.setTo(user.getEmail());
            message.setSubject("[Libris] " + messages.getMessage(key + ".subject", null, locale));
            message.setText(messages.getMessage(key + ".body", new Object[]{link}, locale));
            sender.send(message);
        } catch (Exception e) {
            log.warn("mail delivery failed for user {}: {}", user.getId(), e.getMessage());
        }
    }
}
