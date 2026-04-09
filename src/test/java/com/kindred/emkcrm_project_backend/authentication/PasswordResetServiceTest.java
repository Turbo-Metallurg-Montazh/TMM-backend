package com.kindred.emkcrm_project_backend.authentication;

import com.kindred.emkcrm_project_backend.authentication.rbac.RbacService;
import com.kindred.emkcrm_project_backend.config.EmailProperties;
import com.kindred.emkcrm_project_backend.db.entities.PasswordResetToken;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.PasswordResetTokenRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.ServiceUnavailableException;
import com.kindred.emkcrm_project_backend.services.email.EmailService;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetServiceTest {

    private StubPasswordResetTokenRepository tokenRepository;
    private StubUserService userService;
    private StubEmailService emailService;
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        tokenRepository = new StubPasswordResetTokenRepository();
        userService = new StubUserService();
        emailService = new StubEmailService();
        passwordResetService = new PasswordResetService(
                tokenRepository.proxy(),
                userService,
                emailService,
                new EmailProperties(null, null, "noreply@example.com", null, "https://emk.example/reset")
        );
    }

    @Test
    void sendPasswordResetLinkInvalidatesExistingTokensSavesNewTokenAndSendsEmail() {
        User user = user(15L, "alice", "alice@example.com");
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);

        passwordResetService.sendPasswordResetLink(user);

        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);
        PasswordResetToken savedToken = tokenRepository.requireLastSaved();
        String rawToken = emailService.sentResetUrl.substring(emailService.sentResetUrl.indexOf("token=") + 6);

        assertThat(tokenRepository.lastInvalidatedUserId).isEqualTo(15L);
        assertThat(emailService.sentTo).isEqualTo("alice@example.com");
        assertThat(emailService.sentUsername).isEqualTo("alice");
        assertThat(emailService.sentResetUrl).startsWith("https://emk.example/reset?token=");
        assertThat(savedToken.getUser()).isSameAs(user);
        assertThat(savedToken.getTokenHash()).isEqualTo(sha256(rawToken));
        assertThat(savedToken.getUsedAt()).isNull();
        assertThat(savedToken.getExpiresAt())
                .isAfterOrEqualTo(before.plusMinutes(30))
                .isBeforeOrEqualTo(after.plusMinutes(30).plusSeconds(1));
    }

    @Test
    void sendPasswordResetLinkWrapsMailFailures() {
        emailService.failWithMailException = true;

        assertThatThrownBy(() -> passwordResetService.sendPasswordResetLink(user(15L, "alice", "alice@example.com")))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Не удалось отправить письмо для сброса пароля");
    }

    @Test
    void confirmPasswordResetEncodesPasswordMarksTokenUsedAndInvalidatesActiveTokens() {
        User user = user(20L, "bob", "bob@example.com");
        String rawToken = "raw-token";
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(sha256(rawToken));
        token.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        tokenRepository.add(token);

        passwordResetService.confirmPasswordReset("  " + rawToken + "  ", "new-password");

        assertThat(userService.savedUsers).containsExactly(user);
        assertThat(user.getPassword()).isEqualTo("encoded:new-password");
        assertThat(token.getUsedAt()).isNotNull();
        assertThat(tokenRepository.lastInvalidatedUserId).isEqualTo(20L);
    }

    @Test
    void confirmPasswordResetRejectsExpiredTokens() {
        User user = user(20L, "bob", "bob@example.com");
        String rawToken = "expired-token";
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(sha256(rawToken));
        token.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        tokenRepository.add(token);

        assertThatThrownBy(() -> passwordResetService.confirmPasswordReset(rawToken, "new-password"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ссылка сброса пароля недействительна или истекла");
    }

    @Test
    void confirmPasswordResetRejectsBlankInputs() {
        assertThatThrownBy(() -> passwordResetService.confirmPasswordReset(" ", "new-password"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("token is required");

        assertThatThrownBy(() -> passwordResetService.confirmPasswordReset("token", " "))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("newPassword is required");
    }

    private User user(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("old-password");
        return user;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static final class StubUserService extends UserService {

        private final java.util.List<User> savedUsers = new java.util.ArrayList<>();

        StubUserService() {
            super((UserRepository) null, (RbacService) null, (PasswordEncoder) null);
        }

        @Override
        public void encodePasswordAndSaveUser(User user) {
            user.setPassword("encoded:" + user.getPassword());
            savedUsers.add(user);
        }
    }

    private static final class StubEmailService extends EmailService {

        private String sentTo;
        private String sentUsername;
        private String sentResetUrl;
        private boolean failWithMailException;

        StubEmailService() {
            super(null, new EmailProperties(null, null, "noreply@example.com", null, "https://emk.example/reset"));
        }

        @Override
        public void sendPasswordResetEmail(String to, String username, String resetUrl) throws MessagingException {
            if (failWithMailException) {
                throw new MailSendException("boom");
            }
            this.sentTo = to;
            this.sentUsername = username;
            this.sentResetUrl = resetUrl;
        }
    }

    private static final class StubPasswordResetTokenRepository implements InvocationHandler {

        private final Map<String, PasswordResetToken> tokensByHash = new LinkedHashMap<>();
        private PasswordResetToken lastSaved;
        private Long lastInvalidatedUserId;

        PasswordResetTokenRepository proxy() {
            return (PasswordResetTokenRepository) Proxy.newProxyInstance(
                    PasswordResetTokenRepository.class.getClassLoader(),
                    new Class[]{PasswordResetTokenRepository.class},
                    this
            );
        }

        void add(PasswordResetToken token) {
            tokensByHash.put(token.getTokenHash(), token);
        }

        PasswordResetToken requireLastSaved() {
            if (lastSaved == null) {
                throw new AssertionError("Expected token to be saved");
            }
            return lastSaved;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "save" -> save((PasswordResetToken) args[0]);
                case "findByTokenHash" -> Optional.ofNullable(tokensByHash.get(args[0]));
                case "invalidateActiveTokens" -> invalidateActiveTokens((Long) args[0], (LocalDateTime) args[1]);
                case "toString" -> "StubPasswordResetTokenRepository";
                case "hashCode" -> System.identityHashCode(this);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("Method not implemented in test stub: " + method.getName());
            };
        }

        private PasswordResetToken save(PasswordResetToken token) {
            lastSaved = token;
            tokensByHash.put(token.getTokenHash(), token);
            return token;
        }

        private int invalidateActiveTokens(Long userId, LocalDateTime usedAt) {
            lastInvalidatedUserId = userId;
            int updated = 0;
            for (PasswordResetToken token : tokensByHash.values()) {
                if (token.getUser() != null
                        && userId.equals(token.getUser().getId())
                        && token.getUsedAt() == null
                        && token.getExpiresAt() != null
                        && token.getExpiresAt().isAfter(usedAt)) {
                    token.setUsedAt(usedAt);
                    updated++;
                }
            }
            return updated;
        }
    }
}
