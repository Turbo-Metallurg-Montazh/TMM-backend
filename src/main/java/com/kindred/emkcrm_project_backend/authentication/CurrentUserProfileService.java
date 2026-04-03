package com.kindred.emkcrm_project_backend.authentication;

import com.kindred.emkcrm_project_backend.authentication.rbac.RbacService;
import com.kindred.emkcrm_project_backend.authentication.rbac.SecurityActorService;
import com.kindred.emkcrm_project_backend.db.entities.Role;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.ConflictException;
import com.kindred.emkcrm_project_backend.exception.NotFoundException;
import com.kindred.emkcrm_project_backend.model.UpdateCurrentUserRequest;
import com.kindred.emkcrm_project_backend.model.UserProfileDto;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class CurrentUserProfileService {

    private final UserRepository userRepository;
    private final RbacService rbacService;
    private final SecurityActorService securityActorService;

    public CurrentUserProfileService(
            UserRepository userRepository,
            RbacService rbacService,
            SecurityActorService securityActorService
    ) {
        this.userRepository = userRepository;
        this.rbacService = rbacService;
        this.securityActorService = securityActorService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public UserProfileDto getCurrentUserProfile() {
        User user = requireCurrentUser();
        Set<Role> roles = rbacService.findRolesByUsername(user.getUsername());
        return toDto(user, roles);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public UserProfileDto updateCurrentUserProfile(UpdateCurrentUserRequest request) {
        User user = requireCurrentUser();

        String email = normalizeRequired(request.getEmail(), "email");
        String firstName = normalizeRequired(request.getFirstName(), "firstName");
        String lastName = normalizeRequired(request.getLastName(), "lastName");
        String middleName = resolveMiddleName(request.getMiddleName(), user.getMiddleName());

        User userWithSameEmail = userRepository.findByEmail(email);
        if (userWithSameEmail != null && !userWithSameEmail.getId().equals(user.getId())) {
            throw new ConflictException("Email already taken");
        }

        user.setEmail(email);
        user.setFirstName(firstName);
        user.setMiddleName(middleName);
        user.setLastName(lastName);
        userRepository.save(user);

        Set<Role> roles = rbacService.findRolesByUsername(user.getUsername());
        return toDto(user, roles);
    }

    private User requireCurrentUser() {
        String username = securityActorService.getCurrentUsername();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        return user;
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private String resolveMiddleName(JsonNullable<String> middleName, String currentMiddleName) {
        if (middleName == null || middleName.isUndefined()) {
            return currentMiddleName;
        }
        return normalizeOptional(middleName.orElse(null));
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UserProfileDto toDto(User user, Set<Role> roles) {
        List<String> roleCodes = roles.stream()
                .map(Role::getCode)
                .sorted(String::compareTo)
                .toList();

        return new UserProfileDto()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(roleCodes)
                .firstName(user.getFirstName())
                .middleName(user.getMiddleName())
                .lastName(user.getLastName())
                .enabled(user.isEnabled());
    }
}
