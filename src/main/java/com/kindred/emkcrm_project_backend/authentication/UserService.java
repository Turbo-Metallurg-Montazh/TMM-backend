package com.kindred.emkcrm_project_backend.authentication;

import com.kindred.emkcrm_project_backend.authentication.rbac.RbacService;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import com.kindred.emkcrm_project_backend.exception.UnauthorizedException;
import com.kindred.emkcrm_project_backend.model.LoginRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RbacService rbacService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RbacService rbacService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.rbacService = rbacService;
        this.passwordEncoder = passwordEncoder;
    }


    public void encodePasswordAndSaveUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
    }


    public User validateUsername(LoginRequest loginInfo) {
        User user = findUserWithRolesByCredentials(loginInfo.getUsername(), loginInfo.getEmail());
        if (user != null && passwordEncoder.matches(loginInfo.getPassword(), user.getPassword())) {
            if (!user.isEnabled()) {
                throw new UnauthorizedException("User account is disabled");
            }
            return user;
        }
        throw new UnauthorizedException("Bad login or password");
    }


    public User findUserWithRolesByUsername(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return null;
        }
        user.setRoles(rbacService.findRolesByUsername(username));
        return user;
    }

    public User findUserWithRolesByCredentials(String username, String email) {
        User user = findByUsernameOrEmail(username, email);
        if (user == null) {
            return null;
        }
        user.setRoles(rbacService.findRolesByUsername(user.getUsername()));
        return user;
    }

    private User findByUsernameOrEmail(String username, String email) {
        String normalizedUsername = normalizeCredential(username);
        String normalizedEmail = normalizeCredential(email);

        if (normalizedUsername != null) {
            User user = userRepository.findByUsername(normalizedUsername);
            if (user != null) {
                return user;
            }
        }

        if (normalizedEmail != null) {
            return userRepository.findByEmail(normalizedEmail);
        }

        if (normalizedUsername != null && normalizedUsername.contains("@")) {
            return userRepository.findByEmail(normalizedUsername);
        }

        return null;
    }

    private String normalizeCredential(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
