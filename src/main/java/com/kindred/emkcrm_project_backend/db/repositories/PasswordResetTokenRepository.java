package com.kindred.emkcrm_project_backend.db.repositories;

import com.kindred.emkcrm_project_backend.db.entities.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE PasswordResetToken t
               SET t.usedAt = :usedAt
             WHERE t.user.id = :userId
               AND t.usedAt IS NULL
               AND t.expiresAt > :usedAt
            """)
    int invalidateActiveTokens(@Param("userId") Long userId, @Param("usedAt") LocalDateTime usedAt);

    @Modifying
    @Query("""
            DELETE FROM PasswordResetToken t
             WHERE t.usedAt IS NOT NULL
                OR t.expiresAt <= :now
            """)
    int deleteUsedAndExpired(@Param("now") LocalDateTime now);
}
