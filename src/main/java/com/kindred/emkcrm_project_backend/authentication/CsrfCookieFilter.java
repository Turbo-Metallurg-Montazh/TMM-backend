package com.kindred.emkcrm_project_backend.authentication;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class CsrfCookieFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/login",
            "/refresh",
            "/logout",
            "/password-reset/confirm",
            "/admin/users/reset-password"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (shouldValidate(request) && !hasValidCsrfToken(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Forbidden\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldValidate(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        if (EXCLUDED_PATHS.contains(request.getRequestURI())) {
            return false;
        }
        return WebUtils.getCookie(request, AuthCookieService.ACCESS_TOKEN_COOKIE) != null;
    }

    private boolean hasValidCsrfToken(HttpServletRequest request) {
        var csrfCookie = WebUtils.getCookie(request, AuthCookieService.CSRF_TOKEN_COOKIE);
        String csrfHeader = request.getHeader(AuthCookieService.CSRF_TOKEN_HEADER);
        return csrfCookie != null
                && csrfCookie.getValue() != null
                && !csrfCookie.getValue().isBlank()
                && csrfCookie.getValue().equals(csrfHeader);
    }
}
