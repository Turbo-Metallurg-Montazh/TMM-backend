package com.kindred.emkcrm_project_backend.authentication;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthCookieService {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String CSRF_TOKEN_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_TOKEN_HEADER = "X-XSRF-TOKEN";

    private final boolean secure;
    private final String sameSite;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthCookieService(
            @Value("${security.auth.cookie.secure:true}") boolean secure,
            @Value("${security.auth.cookie.same-site:Strict}") String sameSite,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public void addAuthCookies(
            HttpServletResponse response,
            String accessToken,
            String refreshToken,
            long refreshMaxAgeSeconds,
            String csrfToken
    ) {
        addCookie(response, ACCESS_TOKEN_COOKIE, accessToken, true, Duration.ofSeconds(jwtTokenProvider.getValidityInSeconds()));
        addCookie(response, REFRESH_TOKEN_COOKIE, refreshToken, true, Duration.ofSeconds(refreshMaxAgeSeconds));
        addCookie(response, CSRF_TOKEN_COOKIE, csrfToken, false, Duration.ofSeconds(jwtTokenProvider.getValidityInSeconds()));
    }

    public void clearAuthCookies(HttpServletResponse response) {
        clearCookie(response, ACCESS_TOKEN_COOKIE, true);
        clearCookie(response, REFRESH_TOKEN_COOKIE, true);
        clearCookie(response, CSRF_TOKEN_COOKIE, false);
    }

    private void addCookie(HttpServletResponse response, String name, String value, boolean httpOnly, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge)
                .build()
                .toString());
    }

    private void clearCookie(HttpServletResponse response, String name, boolean httpOnly) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, "")
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build()
                .toString());
    }
}
