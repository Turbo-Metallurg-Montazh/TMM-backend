package com.kindred.emkcrm_project_backend.authentication.rbac;

import com.kindred.emkcrm_project_backend.db.entities.Role;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.PermissionRepository;
import com.kindred.emkcrm_project_backend.db.repositories.RoleRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UserPermissionOverrideRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserPermissionOverrideRepository userPermissionOverrideRepository;

    @Mock
    private RoleChangeAuditService roleChangeAuditService;

    @InjectMocks
    private RbacService rbacService;

    @Test
    void replaceUserRolesResolvesDeveloperRoleByExistingUppercaseCode() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");

        Role developer = new Role();
        developer.setId(10L);
        developer.setCode("DEVELOPER");
        developer.setName("Разработчик системы");

        when(roleRepository.findByCodeWithPermissions("DEVELOPER")).thenReturn(Optional.of(developer));
        when(userRepository.save(user)).thenReturn(user);

        Set<Role> assignedRoles = rbacService.replaceUserRoles(user, List.of("DEVELOPER"), "admin");

        assertThat(assignedRoles).containsExactly(developer);
        assertThat(user.getRoles()).containsExactly(developer);
        verify(roleRepository).findByCodeWithPermissions("DEVELOPER");

        ArgumentCaptor<String> roleCodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(roleChangeAuditService).log(
                eq("admin"),
                eq("alice"),
                eq("USER_ROLE_ASSIGNED"),
                roleCodeCaptor.capture(),
                anyString()
        );
        assertThat(roleCodeCaptor.getValue()).isEqualTo("DEVELOPER");
    }
}
