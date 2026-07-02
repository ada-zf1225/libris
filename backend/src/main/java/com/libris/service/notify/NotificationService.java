package com.libris.service.notify;

import com.libris.domain.patron.Notification;
import com.libris.domain.patron.NotificationRepository;
import com.libris.domain.user.User;
import com.libris.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creates in-app notifications and mirrors them by email in the user's
 * preferred language. Email delivery is best-effort: a down SMTP relay must
 * never break a circulation transaction or a scheduled job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final MessageSource messages;
    private final ObjectProvider<JavaMailSender> mailSender;

    /**
     * @param type stable machine tag, e.g. DUE_SOON / OVERDUE / HOLD_READY
     * @param key  i18n key prefix; {@code <key>.title} and {@code <key>.body} must exist
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyUser(Long userId, String type, String key, Object... args) {
        User user = users.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        Locale locale = Locale.forLanguageTag(user.getPreferredLocale() == null ? "zh-CN" : user.getPreferredLocale());
        String title = messages.getMessage(key + ".title", args, key, locale);
        String body = messages.getMessage(key + ".body", args, key, locale);
        notifications.save(new Notification(userId, type, title, body));
        sendEmail(user, title, body);
    }

    private void sendEmail(User user, String subject, String body) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("Libris <noreply@libris.local>");
            message.setTo(user.getEmail());
            message.setSubject("[Libris] " + subject);
            message.setText(body);
            sender.send(message);
        } catch (Exception e) {
            log.warn("mail delivery failed for user {}: {}", user.getId(), e.getMessage());
        }
    }
}
