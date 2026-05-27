package com.kindred.emkcrm_project_backend.authentication.rbac;

import com.kindred.emkcrm_project_backend.db.entities.Permission;
import com.kindred.emkcrm_project_backend.db.entities.Role;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.entities.UserGroup;
import com.kindred.emkcrm_project_backend.db.entities.UserPermissionOverride;
import com.kindred.emkcrm_project_backend.db.repositories.PermissionRepository;
import com.kindred.emkcrm_project_backend.db.repositories.RoleRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UserPermissionOverrideRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RbacService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;
    private final RoleChangeAuditService roleChangeAuditService;

    public RbacService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserPermissionOverrideRepository userPermissionOverrideRepository,
            RoleChangeAuditService roleChangeAuditService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userPermissionOverrideRepository = userPermissionOverrideRepository;
        this.roleChangeAuditService = roleChangeAuditService;
    }

    @Transactional(readOnly = true)
    public Set<String> resolveAuthorities(String username) {
        User user = userRepository.findByUsernameWithAuthorities(username)
                .orElse(null);
        if (user == null) {
            return Set.of();
        }

        Set<String> authorities = getEffectiveRoles(user).stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .filter(Objects::nonNull)
                .filter(code -> !code.isBlank())
                .collect(Collectors.toSet());

        applyPermissionOverrides(user.getId(), authorities);
        return authorities;
    }

    @Transactional(readOnly = true)
    public Set<Role> findRolesByUsername(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return Set.of();
        }
        return new HashSet<>(user.getRoles());
    }

    @Transactional(readOnly = true)
    public Map<Long, Set<Role>> findRolesByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Set<Role>> result = new HashMap<>();
        for (User user : userRepository.findAllById(userIds)) {
            result.put(user.getId(), new HashSet<>(user.getRoles()));
        }
        return result;
    }

    @Transactional
    public Set<Role> replaceUserRoles(User user, Collection<String> roleCodesOrNames, String actorUsername) {
        Set<Role> resolvedRoles = resolveRoles(roleCodesOrNames);

        Set<Role> previousRoles = new HashSet<>(user.getRoles());
        user.getRoles().clear();
        user.getRoles().addAll(resolvedRoles);
        userRepository.save(user);

        previousRoles.stream()
                .filter(role -> !resolvedRoles.contains(role))
                .forEach(role -> roleChangeAuditService.log(
                        actorUsername,
                        user.getUsername(),
                        "USER_ROLE_REMOVED",
                        role.getCode(),
                        "Role removed during role set replacement"
                ));

        resolvedRoles.stream()
                .filter(role -> !previousRoles.contains(role))
                .forEach(role -> roleChangeAuditService.log(
                        actorUsername,
                        user.getUsername(),
                        "USER_ROLE_ASSIGNED",
                        role.getCode(),
                        "Role assigned during role set replacement"
                ));

        return resolvedRoles;
    }

    @Transactional(readOnly = true)
    public List<Role> listRolesWithPermissions() {
        return roleRepository.findAllWithPermissions();
    }

    @Transactional(readOnly = true)
    public List<Permission> listPermissions() {
        return permissionRepository.findAll().stream()
                .sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
                .collect(Collectors.toList());
    }

    @Transactional
    public Role updateRolePermissions(String roleCode, Collection<String> permissionCodes, String actorUsername) {
        Role role = resolveRole(roleCode);
        Set<Permission> permissions = resolvePermissions(permissionCodes);
        role.setPermissions(permissions);

        Role savedRole = roleRepository.save(role);
        roleChangeAuditService.log(
                actorUsername,
                actorUsername,
                "ROLE_PERMISSIONS_UPDATED",
                savedRole.getCode(),
                "Permissions replaced for role: " + savedRole.getCode()
        );
        return savedRole;
    }

    private Set<Role> getEffectiveRoles(User user) {
        Set<Role> roles = new HashSet<>(user.getRoles());
        for (UserGroup group : user.getGroups()) {
            roles.addAll(group.getRoles());
        }
        return roles;
    }

    private void applyPermissionOverrides(Long userId, Set<String> authorities) {
        List<UserPermissionOverride> overrides = userPermissionOverrideRepository.findByUser_Id(userId);
        for (UserPermissionOverride override : overrides) {
            String permissionCode = override.getPermission().getCode();
            if (override.isGranted()) {
                authorities.add(permissionCode);
            } else {
                authorities.remove(permissionCode);
            }
        }
    }

    private Role resolveRole(String roleCodeOrName) {
        if (roleCodeOrName == null || roleCodeOrName.isBlank()) {
            throw new BadRequestException("Role code/name must not be blank");
        }

        String trimmedRoleCodeOrName = roleCodeOrName.trim();
        String normalizedCode = trimmedRoleCodeOrName.toUpperCase(Locale.ROOT);
        return roleRepository.findByCodeWithPermissions(trimmedRoleCodeOrName)
                .or(() -> roleRepository.findByCodeWithPermissions(normalizedCode))
                .or(() -> roleRepository.findByName(trimmedRoleCodeOrName))
                .orElseThrow(() -> new NotFoundException("Role not found: " + roleCodeOrName));
    }

    private Set<Role> resolveRoles(Collection<String> roleCodesOrNames) {
        if (roleCodesOrNames == null || roleCodesOrNames.isEmpty()) {
            return new HashSet<>();
        }

        Set<Role> roles = new HashSet<>();
        List<String> unresolved = new ArrayList<>();
        for (String roleCodeOrName : roleCodesOrNames) {
            try {
                roles.add(resolveRole(roleCodeOrName));
            } catch (NotFoundException e) {
                unresolved.add(roleCodeOrName);
            }
        }

        if (!unresolved.isEmpty()) {
            throw new NotFoundException("Roles not found: " + String.join(", ", unresolved));
        }
        return roles;
    }

    private Set<Permission> resolvePermissions(Collection<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> normalizedCodes = permissionCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .map(code -> code.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<Permission> found = permissionRepository.findByCodeIn(normalizedCodes);
        Set<String> foundCodes = found.stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        Set<String> missing = new HashSet<>(normalizedCodes);
        missing.removeAll(foundCodes);
        if (!missing.isEmpty()) {
            throw new NotFoundException("Permissions not found: " + String.join(", ", missing));
        }

        return new HashSet<>(found);
    }
}
