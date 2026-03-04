package com.kindred.emkcrm_project_backend.authentication;

import com.kindred.emkcrm_project_backend.config.EmailProperties;
import com.kindred.emkcrm_project_backend.db.entities.PasswordResetToken;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.PasswordResetTokenRepository;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.services.email.EmailService;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class PasswordResetService {

    private static final int TOKEN_TTL_MINUTES = 30;
    private static final String INVALID_OR_EXPIRED_TOKEN_MESSAGE = "Ссылка сброса пароля недействительна или истекла";

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserService userService;
    private final EmailService emailService;
    private final EmailProperties emailProperties;

    public PasswordResetService(
            PasswordResetTokenRepository passwordResetTokenRepository,
            UserService userService,
            EmailService emailService,
            EmailProperties emailProperties
    ) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userService = userService;
        this.emailService = emailService;
        this.emailProperties = emailProperties;
    }

    @Transactional
    public void sendPasswordResetLink(User user) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        passwordResetTokenRepository.invalidateActiveTokens(user.getId(), now);

        String rawToken = generateRawToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(now.plusMinutes(TOKEN_TTL_MINUTES));
        passwordResetTokenRepository.save(token);

        String resetUrl = buildResetUrl(rawToken);
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetUrl);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage(), e);
            throw new BadRequestException("Не удалось отправить письмо для сброса пароля");
        }
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("token is required");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("newPassword is required");
        }

        String tokenHash = hashToken(rawToken.trim());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException(INVALID_OR_EXPIRED_TOKEN_MESSAGE));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
            throw new BadRequestException(INVALID_OR_EXPIRED_TOKEN_MESSAGE);
        }

        User user = token.getUser();
        user.setPassword(newPassword);
        userService.encodePasswordAndSaveUser(user);

        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);
        passwordResetTokenRepository.invalidateActiveTokens(user.getId(), now);
    }

    private String buildResetUrl(String rawToken) {
        String baseUrl = emailProperties.password_reset_url();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("email.password-reset-url is not configured");
        }

        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "token=" + rawToken;
    }

    private String generateRawToken() {
        return UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }
}
