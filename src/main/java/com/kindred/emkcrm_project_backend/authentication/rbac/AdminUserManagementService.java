package com.kindred.emkcrm_project_backend.authentication.rbac;

import com.kindred.emkcrm_project_backend.authentication.UserService;
import com.kindred.emkcrm_project_backend.config.EmailProperties;
import com.kindred.emkcrm_project_backend.db.entities.Role;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.ConflictException;
import com.kindred.emkcrm_project_backend.exception.NotFoundException;
import com.kindred.emkcrm_project_backend.model.AdminCreateUserRequest;
import com.kindred.emkcrm_project_backend.model.AdminResetPasswordRequest;
import com.kindred.emkcrm_project_backend.model.AdminUserDto;
import com.kindred.emkcrm_project_backend.model.MessageResponse;
import com.kindred.emkcrm_project_backend.services.email.EmailService;
import com.kindred.emkcrm_project_backend.utils.PasswordGenerator;
import com.kindred.emkcrm_project_backend.utils.UsernameGenerator;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminUserManagementService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final RbacService rbacService;
    private final SecurityActorService securityActorService;
    private final UsernameGenerator usernameGenerator;
    private final PasswordGenerator passwordGenerator;
    private final EmailService emailService;
    private final EmailProperties emailProperties;

    public AdminUserManagementService(
            UserRepository userRepository,
            UserService userService,
            RbacService rbacService,
            SecurityActorService securityActorService,
            UsernameGenerator usernameGenerator,
            PasswordGenerator passwordGenerator,
            EmailService emailService,
            EmailProperties emailProperties
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.rbacService = rbacService;
        this.securityActorService = securityActorService;
        this.usernameGenerator = usernameGenerator;
        this.passwordGenerator = passwordGenerator;
        this.emailService = emailService;
        this.emailProperties = emailProperties;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('RBAC.USER.READ')")
    public List<AdminUserDto> listUsers() {
        List<User> users = userRepository.findAll();
        Map<Long, Set<Role>> roleMap = rbacService.findRolesByUserIds(users.stream().map(User::getId).collect(Collectors.toSet()));
        return users.stream()
                .map(user -> toDto(user, roleMap.getOrDefault(user.getId(), Set.of())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('RBAC.USER.READ')")
    public AdminUserDto getUserByUsername(String username) {
        User user = requireUser(username);
        Set<Role> roles = rbacService.findRolesByUsername(username);
        return toDto(user, roles);
    }

    @Transactional
    @PreAuthorize("hasAuthority('RBAC.USER.WRITE')")
    public AdminUserDto createUser(AdminCreateUserRequest request) {
        if (userRepository.findByEmail(request.getEmail()) != null) {
            throw new ConflictException("Email already taken");
        }

        String username = usernameGenerator.generateUniqueUsername(
                request.getFirstName(),
                request.getMiddleName().orElse(""),
                request.getLastName()
        );

        String generatedPassword = passwordGenerator.generatePassword();

        User user = new User();
        user.setUsername(username);
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setMiddleName(request.getMiddleName().orElse(null));
        user.setLastName(request.getLastName());
        user.setPassword(generatedPassword);
        user.setEnabled(true);
        userService.encodePasswordAndSaveUser(user);

        User savedUser = userRepository.findByUsername(username);
        Collection<String> requestedRoles = request.getRoles() == null || request.getRoles().isEmpty()
                ? List.of(RbacRoleCodes.SALES_MANAGER)
                : request.getRoles();

        Set<Role> roles = rbacService.replaceUserRoles(
                savedUser,
                requestedRoles,
                securityActorService.getCurrentUsernameOrSystem()
        );

        try {
            emailService.sendRegistrationEmail(savedUser.getEmail(), username, generatedPassword, emailProperties.login_url());
        } catch (MessagingException e) {
            log.error("Failed to send registration email to {}: {}", savedUser.getEmail(), e.getMessage(), e);
        }

        return toDto(savedUser, roles);
    }

    @Transactional
    @PreAuthorize("hasAuthority('RBAC.USER.WRITE')")
    public MessageResponse deleteUserByUsername(String username) {
        User user = requireUser(username);
        userRepository.delete(user);

        MessageResponse response = new MessageResponse();
        response.setMessage(String.format("User %s deleted", username));
        return response;
    }

    @Transactional
    @PreAuthorize("hasAuthority('RBAC.USER.WRITE')")
    public MessageResponse resetUserPassword(String username, AdminResetPasswordRequest request) {
        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new BadRequestException("newPassword is required");
        }

        User user = requireUser(username);
        user.setPassword(request.getNewPassword());
        userService.encodePasswordAndSaveUser(user);

        MessageResponse response = new MessageResponse();
        response.setMessage(String.format("Password for user %s has been reset", username));
        return response;
    }

    private User requireUser(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        return user;
    }

    private AdminUserDto toDto(User user, Set<Role> roles) {
        List<String> roleCodes = roles.stream()
                .map(Role::getCode)
                .sorted(String::compareTo)
                .collect(Collectors.toList());

        return new AdminUserDto()
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(roleCodes)
                .firstName(user.getFirstName())
                .middleName(user.getMiddleName())
                .lastName(user.getLastName());
    }
}
