package com.kindred.emkcrm_project_backend.authentication;

import com.kindred.emkcrm_project_backend.db.repositories.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    @Scheduled(cron = "${schedule.refresh-token-cleanup-cron:0 0 23 * * SUN}", zone = "Europe/Moscow")
    public void cleanupRevokedAndExpiredTokens() {
        int deleted = refreshTokenRepository.deleteRevokedAndExpired(LocalDateTime.now(ZoneOffset.UTC));
        if (deleted > 0) {
            log.info("Refresh token cleanup deleted {} rows", deleted);
        }
    }
}
