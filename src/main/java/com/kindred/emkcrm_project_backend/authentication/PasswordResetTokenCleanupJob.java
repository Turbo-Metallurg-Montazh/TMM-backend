package com.kindred.emkcrm_project_backend.authentication;

import com.kindred.emkcrm_project_backend.db.repositories.PasswordResetTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
public class PasswordResetTokenCleanupJob {

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public PasswordResetTokenCleanupJob(PasswordResetTokenRepository passwordResetTokenRepository) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Transactional
    @Scheduled(cron = "${schedule.password-reset-cleanup-cron:0 0 23 * * SUN}", zone = "Europe/Moscow")
    public void cleanupUsedAndExpiredTokens() {
        int deleted = passwordResetTokenRepository.deleteUsedAndExpired(LocalDateTime.now(ZoneOffset.UTC));
        if (deleted > 0) {
            log.info("Password reset token cleanup deleted {} rows", deleted);
        }
    }
}
