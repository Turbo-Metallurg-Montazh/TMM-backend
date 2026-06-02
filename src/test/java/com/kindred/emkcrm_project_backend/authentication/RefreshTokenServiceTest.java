package com.kindred.emkcrm_project_backend.authentication;

import com.kindred.emkcrm_project_backend.db.entities.RefreshToken;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.RefreshTokenRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private static final long REFRESH_VALIDITY_MILLISECONDS = 604_800_000L;
    private static final long REFRESH_VALIDITY_SECONDS = 604_800L;

    private RefreshTokenRepository refreshTokenRepository;
    private JwtTokenProvider jwtTokenProvider;
    private AuthCookieService authCookieService;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        authCookieService = mock(AuthCookieService.class);
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                jwtTokenProvider,
                authCookieService,
                REFRESH_VALIDITY_MILLISECONDS
        );
        when(jwtTokenProvider.generateAccessToken(anyString())).thenReturn("access-token");
    }

    @Test
    void createSessionStoresRefreshTokenHashForSevenDaysAndIssuesCookies() {
        User user = user();
        MockHttpServletResponse response = new MockHttpServletResponse();
        LocalDateTime before = now();

        refreshTokenService.createSession(user, response);

        LocalDateTime after = now();
        ArgumentCaptor<RefreshToken> savedTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        ArgumentCaptor<String> rawRefreshTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenRepository).save(savedTokenCaptor.capture());
        verify(authCookieService).addAuthCookies(
                eq(response),
                eq("access-token"),
                rawRefreshTokenCaptor.capture(),
                eq(REFRESH_VALIDITY_SECONDS),
                anyString()
        );

        RefreshToken savedToken = savedTokenCaptor.getValue();
        assertThat(savedToken.getUser()).isSameAs(user);
        assertThat(savedToken.getTokenHash()).isEqualTo(hash(rawRefreshTokenCaptor.getValue()));
        assertThat(savedToken.getExpiresAt())
                .isAfterOrEqualTo(before.plusDays(7))
                .isBeforeOrEqualTo(after.plusDays(7).plusSeconds(1));
    }

    @Test
    void refreshSessionRevokesUsedTokenStoresRotatedHashAndIssuesCookies() {
        User user = user();
        String oldRawRefreshToken = "old-refresh-token";
        RefreshToken oldToken = refreshToken(user, hash(oldRawRefreshToken), now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(hash(oldRawRefreshToken))).thenReturn(Optional.of(oldToken));

        MockHttpServletRequest request = requestWithRefreshToken(oldRawRefreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        LocalDateTime before = now();

        refreshTokenService.refreshSession(request, response);

        LocalDateTime after = now();
        ArgumentCaptor<RefreshToken> savedTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        ArgumentCaptor<String> newRawRefreshTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenRepository).findByTokenHash(hash(oldRawRefreshToken));
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(savedTokenCaptor.capture());
        verify(authCookieService).addAuthCookies(
                eq(response),
                eq("access-token"),
                newRawRefreshTokenCaptor.capture(),
                eq(REFRESH_VALIDITY_SECONDS),
                anyString()
        );

        RefreshToken rotatedToken = savedTokenCaptor.getAllValues().get(1);
        String newRawRefreshToken = newRawRefreshTokenCaptor.getValue();
        assertThat(newRawRefreshToken).isNotEqualTo(oldRawRefreshToken);
        assertThat(oldToken.getRevokedAt()).isNotNull();
        assertThat(oldToken.getLastUsedAt()).isNotNull();
        assertThat(oldToken.getReplacedByTokenHash()).isEqualTo(hash(newRawRefreshToken));
        assertThat(rotatedToken.getTokenHash()).isEqualTo(hash(newRawRefreshToken));
        assertThat(rotatedToken.getUser()).isSameAs(user);
        assertThat(rotatedToken.getExpiresAt())
                .isAfterOrEqualTo(before.plusDays(7))
                .isBeforeOrEqualTo(after.plusDays(7).plusSeconds(1));
    }

    @Test
    void logoutRevokesCurrentRefreshTokenAndClearsCookies() {
        User user = user();
        String rawRefreshToken = "refresh-token";
        RefreshToken refreshToken = refreshToken(user, hash(rawRefreshToken), now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))).thenReturn(Optional.of(refreshToken));

        MockHttpServletRequest request = requestWithRefreshToken(rawRefreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        refreshTokenService.logout(request, response);

        verify(refreshTokenRepository).findByTokenHash(hash(rawRefreshToken));
        verify(refreshTokenRepository).save(refreshToken);
        verify(authCookieService).clearAuthCookies(response);
        assertThat(refreshToken.getRevokedAt()).isNotNull();
    }

    private MockHttpServletRequest requestWithRefreshToken(String rawRefreshToken) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, rawRefreshToken));
        return request;
    }

    private RefreshToken refreshToken(User user, String tokenHash, LocalDateTime expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(expiresAt);
        return refreshToken;
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEnabled(true);
        return user;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
