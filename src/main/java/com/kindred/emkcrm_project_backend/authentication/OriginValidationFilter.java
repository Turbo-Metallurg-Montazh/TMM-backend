package com.kindred.emkcrm_project_backend.authentication;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Getter
public class OriginValidationFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> allowedOrigins;

    public OriginValidationFilter(Set<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!SAFE_METHODS.contains(request.getMethod()) && !isAllowedBrowserOrigin(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Forbidden\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowedBrowserOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return allowedOrigins.contains(origin);
        }

        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return true;
        }

        try {
            URI refererUri = URI.create(referer);
            String refererOrigin = refererUri.getScheme() + "://" + refererUri.getAuthority();
            return allowedOrigins.contains(refererOrigin);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
