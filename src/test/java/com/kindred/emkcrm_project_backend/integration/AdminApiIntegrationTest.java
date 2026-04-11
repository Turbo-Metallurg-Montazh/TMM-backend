package com.kindred.emkcrm_project_backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kindred.emkcrm_project_backend.api.AdminUsersApiController;
import com.kindred.emkcrm_project_backend.api.RbacAdminApiController;
import com.kindred.emkcrm_project_backend.authentication.JwtTokenProvider;
import com.kindred.emkcrm_project_backend.authentication.PasswordResetService;
import com.kindred.emkcrm_project_backend.authentication.SecurityConfig;
import com.kindred.emkcrm_project_backend.authentication.UserDetail;
import com.kindred.emkcrm_project_backend.authentication.UserService;
import com.kindred.emkcrm_project_backend.authentication.impl.AdminUsersApiDelegateImpl;
import com.kindred.emkcrm_project_backend.authentication.impl.RbacAdminApiDelegateImpl;
import com.kindred.emkcrm_project_backend.authentication.rbac.AdminUserManagementService;
import com.kindred.emkcrm_project_backend.authentication.rbac.RoleAdministrationService;
import com.kindred.emkcrm_project_backend.authentication.rbac.RbacService;
import com.kindred.emkcrm_project_backend.authentication.rbac.SecurityActorService;
import com.kindred.emkcrm_project_backend.config.EmailProperties;
import com.kindred.emkcrm_project_backend.config.JacksonConfig;
import com.kindred.emkcrm_project_backend.db.entities.Permission;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import com.kindred.emkcrm_project_backend.model.MessageResponse;
import com.kindred.emkcrm_project_backend.model.SendPasswordResetLinkRequest;
import com.kindred.emkcrm_project_backend.exception.GlobalExceptionHandler;
import com.kindred.emkcrm_project_backend.services.email.EmailService;
import com.kindred.emkcrm_project_backend.utils.PasswordGenerator;
import com.kindred.emkcrm_project_backend.utils.UsernameGenerator;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = AdminApiIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "security.jwt.token.secret-key=12345678901234567890123456789012"
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdminApiIntegrationTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper;

    private final JwtTokenProvider jwtTokenProvider;

    private final WebApplicationContext webApplicationContext;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RbacService rbacService;

    @MockitoBean
    private SecurityActorService securityActorService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UsernameGenerator usernameGenerator;

    @MockitoBean
    private PasswordGenerator passwordGenerator;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private UserDetail userDetail;

    AdminApiIntegrationTest(
            ObjectMapper objectMapper,
            JwtTokenProvider jwtTokenProvider,
            WebApplicationContext webApplicationContext
    ) {
        this.objectMapper = objectMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.webApplicationContext = webApplicationContext;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(webApplicationContext.getBean("springSecurityFilterChain", Filter.class))
                .build();

        when(userDetail.loadUserByUsername("reader")).thenReturn(user("reader", "RBAC.USER.READ"));
        when(userDetail.loadUserByUsername("writer")).thenReturn(user("writer", "RBAC.USER.WRITE"));
        when(userDetail.loadUserByUsername("permission-reader")).thenReturn(user("permission-reader", "RBAC.PERMISSION.READ"));
        when(userDetail.loadUserByUsername("regular")).thenReturn(user("regular"));
    }

    @Test
    void resetUserPasswordIsAvailableWithoutAuthentication() throws Exception {
        SendPasswordResetLinkRequest request = new SendPasswordResetLinkRequest();
        request.setEmail("alice@example.com");

        User user = userEntity(1L, "alice", "alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(user);

        mockMvc.perform(post("/admin/users/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Ссылка для сброса пароля отправлена на alice@example.com"));
    }

    @Test
    void listUsersRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Unauthorized\"}"));
    }

    @Test
    void listUsersReturnsForbiddenWithoutReadAuthority() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .header("Authorization", bearerToken("regular")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verifyNoInteractions(userRepository, rbacService);
    }

    @Test
    void listUsersReturnsPayloadForReader() throws Exception {
        User user = userEntity(1L, "alice", "alice@example.com");
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(rbacService.findRolesByUserIds(Set.of(1L))).thenReturn(Map.of(1L, Set.of()));

        mockMvc.perform(get("/admin/users")
                        .header("Authorization", bearerToken("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));
    }

    @Test
    void listPermissionsReturnsForbiddenWithoutPermissionReadAuthority() throws Exception {
        mockMvc.perform(get("/admin/permissions")
                        .header("Authorization", bearerToken("regular")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verifyNoInteractions(rbacService);
    }

    @Test
    void listPermissionsReturnsPayloadForPermissionReader() throws Exception {
        Permission permission = new Permission();
        permission.setCode("RBAC.USER.READ");
        permission.setDescription("Read users");
        when(rbacService.listPermissions()).thenReturn(List.of(permission));

        mockMvc.perform(get("/admin/permissions")
                        .header("Authorization", bearerToken("permission-reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("RBAC.USER.READ"))
                .andExpect(jsonPath("$[0].description").value("Read users"));
    }

    private User userEntity(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName("Alice");
        user.setLastName("Doe");
        user.setPassword("encoded");
        user.setEnabled(true);
        return user;
    }

    private UserDetails user(String username, String... authorities) {
        return org.springframework.security.core.userdetails.User.withUsername(username)
                .password("ignored")
                .authorities(authorities)
                .build();
    }

    private String bearerToken(String username) {
        return "Bearer " + jwtTokenProvider.generateToken(username);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @Import({
            JacksonConfig.class,
            SecurityConfig.class,
            GlobalExceptionHandler.class,
            JwtTokenProvider.class,
            AdminUsersApiController.class,
            AdminUsersApiDelegateImpl.class,
            AdminUserManagementService.class,
            RbacAdminApiController.class,
            RbacAdminApiDelegateImpl.class,
            RoleAdministrationService.class
    })
    static class TestApplication {

        @Bean
        EmailProperties emailProperties() {
            return new EmailProperties(
                    "example.com",
                    "https://example.com/activate",
                    "noreply@example.com",
                    "https://example.com/login",
                    "https://example.com/reset"
            );
        }
    }
}
