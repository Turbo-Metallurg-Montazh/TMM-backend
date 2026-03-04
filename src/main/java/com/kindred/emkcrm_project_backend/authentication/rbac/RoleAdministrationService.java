package com.kindred.emkcrm_project_backend.authentication.rbac;

import com.kindred.emkcrm_project_backend.db.entities.Permission;
import com.kindred.emkcrm_project_backend.db.entities.Role;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import com.kindred.emkcrm_project_backend.exception.NotFoundException;
import com.kindred.emkcrm_project_backend.model.AdminUserDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleAdministrationService {

    private final UserRepository userRepository;
    private final RbacService rbacService;
    private final SecurityActorService securityActorService;

    public RoleAdministrationService(
            UserRepository userRepository,
            RbacService rbacService,
            SecurityActorService securityActorService
    ) {
        this.userRepository = userRepository;
        this.rbacService = rbacService;
        this.securityActorService = securityActorService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('RBAC.ROLE.READ')")
    public List<Role> listRolesWithPermissions() {
        return rbacService.listRolesWithPermissions();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('RBAC.PERMISSION.READ')")
    public List<Permission> listPermissions() {
        return rbacService.listPermissions();
    }

    @Transactional
    @PreAuthorize("hasAuthority('RBAC.ROLE.WRITE')")
    public Role updateRolePermissions(String roleCode, Collection<String> permissionCodes) {
        return rbacService.updateRolePermissions(roleCode, permissionCodes, securityActorService.getCurrentUsernameOrSystem());
    }

    @Transactional
    @PreAuthorize("hasAuthority('RBAC.USER.WRITE')")
    public AdminUserDto updateUserRoles(String username, Collection<String> roles) {
        User user = requireUser(username);
        Set<Role> assignedRoles = rbacService.replaceUserRoles(user, roles, securityActorService.getCurrentUsernameOrSystem());
        List<String> roleCodes = assignedRoles.stream()
                .map(Role::getCode)
                .sorted()
                .collect(Collectors.toList());

        return new AdminUserDto()
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(roleCodes)
                .firstName(user.getFirstName())
                .middleName(user.getMiddleName())
                .lastName(user.getLastName());
    }

    private User requireUser(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        return user;
    }
}
