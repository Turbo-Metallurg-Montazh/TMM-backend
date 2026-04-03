package com.kindred.emkcrm_project_backend.authentication.rbac;

import com.kindred.emkcrm_project_backend.exception.UnauthorizedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityActorService {

    public String getCurrentUsername() {
        String currentUsername = extractCurrentUsername();
        if (currentUsername == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return currentUsername;
    }

    public String getCurrentUsernameOrSystem() {
        String currentUsername = extractCurrentUsername();
        if (currentUsername == null) {
            return "system";
        }
        return currentUsername;
    }

    private String extractCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            return null;
        }

        return username;
    }
}
