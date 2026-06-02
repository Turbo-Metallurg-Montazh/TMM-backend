package com.kindred.emkcrm_project_backend.authentication;

import com.kindred.emkcrm_project_backend.db.entities.RefreshToken;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.RefreshTokenRepository;
import com.kindred.emkcrm_project_backend.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 64;
    private static final int CSRF_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthCookieService authCookieService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshValidityInMilliseconds;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider jwtTokenProvider,
            AuthCookieService authCookieService,
            @Value("${security.jwt.refresh-token.expire-length:604800000}") long refreshValidityInMilliseconds
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authCookieService = authCookieService;
        this.refreshValidityInMilliseconds = refreshValidityInMilliseconds;
    }

    @Transactional
    public void createSession(User user, HttpServletResponse response) {
        String rawRefreshToken = generateRandomToken(TOKEN_BYTES);
        RefreshToken refreshToken = buildRefreshToken(user, hashToken(rawRefreshToken), now());
        refreshTokenRepository.save(refreshToken);
        issueCookies(user, rawRefreshToken, response);
    }

    @Transactional
    public void refreshSession(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = getRefreshToken(request);
        if (rawRefreshToken == null) {
            authCookieService.clearAuthCookies(response);
            throw new UnauthorizedException("Unauthorized");
        }

        LocalDateTime now = now();
        RefreshToken existingToken = refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .orElseThrow(() -> {
                    authCookieService.clearAuthCookies(response);
                    return new UnauthorizedException("Unauthorized");
                });

        if (existingToken.isRevoked()) {
            refreshTokenRepository.revokeActiveTokens(existingToken.getUser().getId(), now);
            authCookieService.clearAuthCookies(response);
            throw new UnauthorizedException("Unauthorized");
        }

        if (!existingToken.getExpiresAt().isAfter(now) || !existingToken.getUser().isEnabled()) {
            existingToken.setRevokedAt(now);
            refreshTokenRepository.save(existingToken);
            authCookieService.clearAuthCookies(response);
            throw new UnauthorizedException("Unauthorized");
        }

        String newRawRefreshToken = generateRandomToken(TOKEN_BYTES);
        String newRefreshHash = hashToken(newRawRefreshToken);
        existingToken.setRevokedAt(now);
        existingToken.setLastUsedAt(now);
        existingToken.setReplacedByTokenHash(newRefreshHash);
        refreshTokenRepository.save(existingToken);

        RefreshToken newRefreshToken = buildRefreshToken(existingToken.getUser(), newRefreshHash, now);
        refreshTokenRepository.save(newRefreshToken);
        issueCookies(existingToken.getUser(), newRawRefreshToken, response);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = getRefreshToken(request);
        if (rawRefreshToken != null) {
            refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                    .filter(token -> token.getRevokedAt() == null)
                    .ifPresent(token -> {
                        token.setRevokedAt(now());
                        refreshTokenRepository.save(token);
                    });
        }
        authCookieService.clearAuthCookies(response);
    }

    @Transactional
    public void revokeActiveTokens(User user) {
        if (user != null && user.getId() != null) {
            refreshTokenRepository.revokeActiveTokens(user.getId(), now());
        }
    }

    private RefreshToken buildRefreshToken(User user, String refreshHash, LocalDateTime now) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(refreshHash);
        refreshToken.setExpiresAt(now.plusSeconds(refreshValidityInMilliseconds / 1000));
        return refreshToken;
    }

    private void issueCookies(User user, String rawRefreshToken, HttpServletResponse response) {
        authCookieService.addAuthCookies(
                response,
                jwtTokenProvider.generateAccessToken(user.getUsername()),
                rawRefreshToken,
                refreshValidityInMilliseconds / 1000,
                generateRandomToken(CSRF_BYTES)
        );
    }

    private String getRefreshToken(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, AuthCookieService.REFRESH_TOKEN_COOKIE);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return null;
        }
        return cookie.getValue();
    }

    private String generateRandomToken(int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
