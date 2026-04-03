package com.kindred.emkcrm_project_backend.authentication;

import com.kindred.emkcrm_project_backend.authentication.rbac.RbacService;
import com.kindred.emkcrm_project_backend.authentication.rbac.SecurityActorService;
import com.kindred.emkcrm_project_backend.authentication.rbac.RoleChangeAuditService;
import com.kindred.emkcrm_project_backend.db.entities.Role;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.PermissionRepository;
import com.kindred.emkcrm_project_backend.db.repositories.RoleRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UserPermissionOverrideRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import com.kindred.emkcrm_project_backend.exception.ConflictException;
import com.kindred.emkcrm_project_backend.exception.UnauthorizedException;
import com.kindred.emkcrm_project_backend.model.UpdateCurrentUserRequest;
import com.kindred.emkcrm_project_backend.model.UserProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserProfileServiceTest {

    private StubUserRepository userRepository;
    private StubRbacService rbacService;
    private StubSecurityActorService securityActorService;
    private CurrentUserProfileService currentUserProfileService;

    @BeforeEach
    void setUp() {
        userRepository = new StubUserRepository();
        rbacService = new StubRbacService();
        securityActorService = new StubSecurityActorService();
        currentUserProfileService = new CurrentUserProfileService(userRepository.proxy(), rbacService, securityActorService);
    }

    @Test
    void getCurrentUserProfileReturnsCurrentUserFromToken() {
        User user = user(7L, "alice", "alice@example.com", "Alice", "Marie", "Cooper", true, "encoded-password");
        userRepository.addUser(user);
        rbacService.setRoles("alice", Set.of(role(1L, "SALES_MANAGER"), role(2L, "ADMIN")));
        securityActorService.setCurrentUsername("alice");

        UserProfileDto result = currentUserProfileService.getCurrentUserProfile();

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getFirstName()).isEqualTo("Alice");
        assertThat(result.getMiddleName()).isEqualTo("Marie");
        assertThat(result.getLastName()).isEqualTo("Cooper");
        assertThat(result.getEnabled()).isTrue();
        assertThat(result.getRoles()).containsExactly("ADMIN", "SALES_MANAGER");
    }

    @Test
    void updateCurrentUserProfileUpdatesOnlyEditableFields() {
        User user = user(7L, "alice", "alice@example.com", "Alice", "Marie", "Cooper", false, "encoded-password");
        UpdateCurrentUserRequest request = new UpdateCurrentUserRequest();
        request.setEmail("  alice.new@example.com  ");
        request.setFirstName("  Alicia  ");
        request.setMiddleName(JsonNullable.of("   "));
        request.setLastName("  Smith  ");

        userRepository.addUser(user);
        rbacService.setRoles("alice", Set.of(role(1L, "SALES_MANAGER"), role(2L, "ADMIN")));
        securityActorService.setCurrentUsername("alice");

        UserProfileDto result = currentUserProfileService.updateCurrentUserProfile(request);

        assertThat(userRepository.wasSaved(user)).isTrue();
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getPassword()).isEqualTo("encoded-password");
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getEmail()).isEqualTo("alice.new@example.com");
        assertThat(user.getFirstName()).isEqualTo("Alicia");
        assertThat(user.getMiddleName()).isNull();
        assertThat(user.getLastName()).isEqualTo("Smith");

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getEmail()).isEqualTo("alice.new@example.com");
        assertThat(result.getFirstName()).isEqualTo("Alicia");
        assertThat(result.getMiddleName()).isNull();
        assertThat(result.getLastName()).isEqualTo("Smith");
        assertThat(result.getEnabled()).isFalse();
        assertThat(result.getRoles()).containsExactly("ADMIN", "SALES_MANAGER");
    }

    @Test
    void updateCurrentUserProfileThrowsConflictWhenEmailBelongsToAnotherUser() {
        User currentUser = user(7L, "alice", "alice@example.com", "Alice", "Marie", "Cooper", true, "encoded-password");
        User conflictingUser = user(9L, "bob", "shared@example.com", "Bob", null, "Brown", true, "other-password");
        UpdateCurrentUserRequest request = new UpdateCurrentUserRequest();
        request.setEmail("shared@example.com");
        request.setFirstName("Alice");
        request.setLastName("Cooper");

        userRepository.addUser(currentUser);
        userRepository.addUser(conflictingUser);
        securityActorService.setCurrentUsername("alice");

        assertThatThrownBy(() -> currentUserProfileService.updateCurrentUserProfile(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already taken");

        assertThat(userRepository.wasSaved(currentUser)).isFalse();
    }

    @Test
    void getCurrentUserProfileThrowsUnauthorizedWhenCurrentUserIsMissing() {
        assertThatThrownBy(() -> currentUserProfileService.getCurrentUserProfile())
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Unauthorized");
    }

    private User user(
            Long id,
            String username,
            String email,
            String firstName,
            String middleName,
            String lastName,
            boolean enabled,
            String password
    ) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setMiddleName(middleName);
        user.setLastName(lastName);
        user.setEnabled(enabled);
        user.setPassword(password);
        return user;
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        return role;
    }

    private static final class StubSecurityActorService extends SecurityActorService {

        private String currentUsername;

        void setCurrentUsername(String currentUsername) {
            this.currentUsername = currentUsername;
        }

        @Override
        public String getCurrentUsername() {
            if (currentUsername == null) {
                throw new UnauthorizedException("Unauthorized");
            }
            return currentUsername;
        }

        @Override
        public String getCurrentUsernameOrSystem() {
            return currentUsername == null ? "system" : currentUsername;
        }
    }

    private static final class StubRbacService extends RbacService {

        private final Map<String, Set<Role>> rolesByUsername = new LinkedHashMap<>();

        StubRbacService() {
            super(
                    (UserRepository) null,
                    (RoleRepository) null,
                    (PermissionRepository) null,
                    (UserPermissionOverrideRepository) null,
                    (RoleChangeAuditService) null
            );
        }

        void setRoles(String username, Set<Role> roles) {
            rolesByUsername.put(username, roles);
        }

        @Override
        public Set<Role> findRolesByUsername(String username) {
            return rolesByUsername.getOrDefault(username, Set.of());
        }
    }

    private static final class StubUserRepository implements InvocationHandler {

        private final Map<String, User> usersByUsername = new LinkedHashMap<>();
        private final Map<String, User> usersByEmail = new LinkedHashMap<>();
        private final List<User> savedUsers = new ArrayList<>();

        UserRepository proxy() {
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class[]{UserRepository.class},
                    this
            );
        }

        void addUser(User user) {
            usersByUsername.put(user.getUsername(), user);
            usersByEmail.put(user.getEmail(), user);
        }

        boolean wasSaved(User user) {
            return savedUsers.contains(user);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "findByUsername" -> usersByUsername.get(args[0]);
                case "findByEmail" -> usersByEmail.get(args[0]);
                case "save" -> save((User) args[0]);
                case "toString" -> "StubUserRepository";
                case "hashCode" -> System.identityHashCode(this);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("Method not implemented in test stub: " + method.getName());
            };
        }

        private User save(User user) {
            usersByUsername.entrySet().removeIf(entry -> entry.getValue() == user);
            usersByEmail.entrySet().removeIf(entry -> entry.getValue() == user);
            usersByUsername.put(user.getUsername(), user);
            usersByEmail.put(user.getEmail(), user);
            savedUsers.add(user);
            return user;
        }
    }
}
