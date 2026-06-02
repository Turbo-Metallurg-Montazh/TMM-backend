package com.kindred.emkcrm_project_backend.authentication;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class AuthCookieService {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String CSRF_TOKEN_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_TOKEN_HEADER = "X-XSRF-TOKEN";

    private final boolean secure;
    private final String sameSite;
    private final String domain;
    private final Set<String> cleanupDomains;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthCookieService(
            @Value("${security.auth.cookie.secure:true}") boolean secure,
            @Value("${security.auth.cookie.same-site:Strict}") String sameSite,
            @Value("${security.auth.cookie.domain:}") String domain,
            @Value("${security.auth.cookie.cleanup-domains:api.turbo-metallurg-montazh.ru,turbo-metallurg-montazh.ru}") String cleanupDomains,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.domain = normalizeDomain(domain);
        this.cleanupDomains = parseCleanupDomains(cleanupDomains);
        if (this.domain != null) {
            this.cleanupDomains.add(this.domain);
        }
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public void addAuthCookies(
            HttpServletResponse response,
            String accessToken,
            String refreshToken,
            long refreshMaxAgeSeconds,
            String csrfToken
    ) {
        clearAuthCookies(response);
        addCookie(response, ACCESS_TOKEN_COOKIE, accessToken, true, Duration.ofSeconds(jwtTokenProvider.getValidityInSeconds()));
        addCookie(response, REFRESH_TOKEN_COOKIE, refreshToken, true, Duration.ofSeconds(refreshMaxAgeSeconds));
        addCookie(response, CSRF_TOKEN_COOKIE, csrfToken, false, Duration.ofSeconds(jwtTokenProvider.getValidityInSeconds()));
    }

    public void clearAuthCookies(HttpServletResponse response) {
        clearCookieVariants(response, ACCESS_TOKEN_COOKIE, true);
        clearCookieVariants(response, REFRESH_TOKEN_COOKIE, true);
        clearCookieVariants(response, CSRF_TOKEN_COOKIE, false);
    }

    private void addCookie(HttpServletResponse response, String name, String value, boolean httpOnly, Duration maxAge) {
        var cookie = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge);
        addCookieDomain(cookie, domain);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }

    private void clearCookieVariants(HttpServletResponse response, String name, boolean httpOnly) {
        clearCookie(response, name, httpOnly, null);
        cleanupDomains.forEach(cleanupDomain -> clearCookie(response, name, httpOnly, cleanupDomain));
    }

    private void clearCookie(HttpServletResponse response, String name, boolean httpOnly, String cookieDomain) {
        var cookie = ResponseCookie.from(name, "")
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ZERO);
        addCookieDomain(cookie, cookieDomain);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }

    private void addCookieDomain(ResponseCookie.ResponseCookieBuilder cookie, String cookieDomain) {
        if (cookieDomain != null) {
            cookie.domain(cookieDomain);
        }
    }

    private Set<String> parseCleanupDomains(String domains) {
        Set<String> parsedDomains = new LinkedHashSet<>();
        if (domains == null || domains.isBlank()) {
            return parsedDomains;
        }
        Arrays.stream(domains.split(","))
                .map(this::normalizeDomain)
                .filter(domain -> domain != null)
                .forEach(parsedDomains::add);
        return parsedDomains;
    }

    private String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        return domain.trim().replaceFirst("^\\.", "");
    }
}
