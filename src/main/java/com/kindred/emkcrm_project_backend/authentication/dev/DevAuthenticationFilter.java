package com.kindred.emkcrm_project_backend.authentication.dev;

import com.kindred.emkcrm_project_backend.db.entities.Permission;
import com.kindred.emkcrm_project_backend.db.repositories.PermissionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Profile("dev")
public class DevAuthenticationFilter extends OncePerRequestFilter {

    private final PermissionRepository permissionRepository;
    private final String username;

    public DevAuthenticationFilter(
            PermissionRepository permissionRepository,
            @Value("${security.dev-auth.username:dev-user}") String username
    ) {
        this.permissionRepository = permissionRepository;
        this.username = username;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(username, null, loadAllAuthorities());

            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }

        filterChain.doFilter(request, response);
    }

    private java.util.List<SimpleGrantedAuthority> loadAllAuthorities() {
        return permissionRepository.findAll().stream()
                .map(Permission::getCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
