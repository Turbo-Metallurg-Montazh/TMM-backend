package com.kindred.emkcrm_project_backend.authentication;

import com.kindred.emkcrm_project_backend.authentication.rbac.RbacService;
import com.kindred.emkcrm_project_backend.db.entities.Role;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.PermissionRepository;
import com.kindred.emkcrm_project_backend.db.repositories.RoleRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UserPermissionOverrideRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import com.kindred.emkcrm_project_backend.exception.AccountDisabledException;
import com.kindred.emkcrm_project_backend.exception.UnauthorizedException;
import com.kindred.emkcrm_project_backend.model.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {

    private StubUserRepository userRepository;
    private StubRbacService rbacService;
    private StubPasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = new StubUserRepository();
        rbacService = new StubRbacService();
        passwordEncoder = new StubPasswordEncoder();
        userService = new UserService(userRepository.proxy(), rbacService, passwordEncoder);
    }

    @Test
    void validateUsernameReturnsUserWhenEmailCredentialMatches() {
        User user = user("alice", "alice@example.com", "encoded:secret", true);
        userRepository.add(user);
        rbacService.setRoles("alice", Set.of(role("ADMIN")));

        LoginRequest request = new LoginRequest();
        request.setEmail("alice@example.com");
        request.setPassword("secret");

        User result = userService.validateUsername(request);

        assertThat(result).isSameAs(user);
        assertThat(result.getRoles()).extracting(Role::getCode).containsExactly("ADMIN");
    }

    @Test
    void validateUsernameSupportsUsernameFieldContainingEmail() {
        User user = user("alice", "alice@example.com", "encoded:secret", true);
        userRepository.add(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("alice@example.com");
        request.setPassword("secret");

        User result = userService.validateUsername(request);

        assertThat(result).isSameAs(user);
    }

    @Test
    void validateUsernameRejectsDisabledAccounts() {
        User user = user("alice", "alice@example.com", "encoded:secret", false);
        userRepository.add(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");

        assertThatThrownBy(() -> userService.validateUsername(request))
                .isInstanceOf(AccountDisabledException.class)
                .hasMessage("User account is disabled");
    }

    @Test
    void validateUsernameRejectsWrongPassword() {
        User user = user("alice", "alice@example.com", "encoded:secret", true);
        userRepository.add(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong");

        assertThatThrownBy(() -> userService.validateUsername(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Bad login or password");
    }

    private User user(String username, String email, String password, boolean enabled) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);
        user.setEnabled(enabled);
        return user;
    }

    private Role role(String code) {
        Role role = new Role();
        role.setCode(code);
        return role;
    }

    private static final class StubPasswordEncoder implements PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encode(rawPassword).equals(encodedPassword);
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
                    null
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

        UserRepository proxy() {
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class[]{UserRepository.class},
                    this
            );
        }

        void add(User user) {
            usersByUsername.put(user.getUsername(), user);
            usersByEmail.put(user.getEmail(), user);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "findByUsername" -> usersByUsername.get(args[0]);
                case "findByEmail" -> usersByEmail.get(args[0]);
                case "findOptionalByUsername" -> Optional.ofNullable(usersByUsername.get(args[0]));
                case "save" -> args[0];
                case "toString" -> "StubUserRepository";
                case "hashCode" -> System.identityHashCode(this);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("Method not implemented in test stub: " + method.getName());
            };
        }
    }
}
