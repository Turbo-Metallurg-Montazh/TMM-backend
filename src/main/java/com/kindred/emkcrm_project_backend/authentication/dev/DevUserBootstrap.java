package com.kindred.emkcrm_project_backend.authentication.dev;

import com.kindred.emkcrm_project_backend.authentication.rbac.RbacRoleCodes;
import com.kindred.emkcrm_project_backend.db.entities.Role;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.RoleRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevUserBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;

    public DevUserBootstrap(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            @Value("${security.dev-auth.username:dev-user}") String username,
            @Value("${security.dev-auth.email:dev-user@example.local}") String email
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {
        Role developerRole = roleRepository.findByCode(RbacRoleCodes.DEVELOPER)
                .orElseThrow(() -> new IllegalStateException("DEVELOPER role is missing"));

        User user = userRepository.findByUsername(username);
        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode("dev"));
        }

        user.setEmail(email);
        user.setFirstName("Dev");
        user.setLastName("User");
        user.setEnabled(true);

        boolean hasDeveloperRole = user.getRoles().stream()
                .anyMatch(role -> RbacRoleCodes.DEVELOPER.equals(role.getCode()));
        if (!hasDeveloperRole) {
            user.addRoles(developerRole);
        }

        userRepository.save(user);
    }
}
