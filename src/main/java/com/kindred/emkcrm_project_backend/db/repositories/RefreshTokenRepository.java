package com.kindred.emkcrm_project_backend.db.repositories;

import com.kindred.emkcrm_project_backend.db.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :revokedAt
             WHERE t.user.id = :userId
               AND t.revokedAt IS NULL
               AND t.expiresAt > :revokedAt
            """)
    int revokeActiveTokens(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying
    @Query("""
            DELETE FROM RefreshToken t
             WHERE t.revokedAt IS NOT NULL
                OR t.expiresAt <= :now
            """)
    int deleteRevokedAndExpired(@Param("now") LocalDateTime now);
}
