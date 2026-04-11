package com.kindred.emkcrm_project_backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kindred.emkcrm_project_backend.api.UsersApiController;
import com.kindred.emkcrm_project_backend.authentication.CurrentUserProfileService;
import com.kindred.emkcrm_project_backend.authentication.JwtTokenProvider;
import com.kindred.emkcrm_project_backend.authentication.SecurityConfig;
import com.kindred.emkcrm_project_backend.authentication.UserDetail;
import com.kindred.emkcrm_project_backend.authentication.impl.UsersApiDelegateImpl;
import com.kindred.emkcrm_project_backend.config.JacksonConfig;
import com.kindred.emkcrm_project_backend.exception.ConflictException;
import com.kindred.emkcrm_project_backend.exception.GlobalExceptionHandler;
import com.kindred.emkcrm_project_backend.model.UpdateCurrentUserRequest;
import com.kindred.emkcrm_project_backend.model.UserProfileDto;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = UsersApiIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "security.jwt.token.secret-key=12345678901234567890123456789012"
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UsersApiIntegrationTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper;

    private final JwtTokenProvider jwtTokenProvider;

    private final WebApplicationContext webApplicationContext;

    @MockitoBean
    private CurrentUserProfileService currentUserProfileService;

    @MockitoBean
    private UserDetail userDetail;

    UsersApiIntegrationTest(
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

        when(userDetail.loadUserByUsername("alice")).thenReturn(authenticatedUser("alice"));
    }

    @Test
    void getCurrentUserProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Unauthorized\"}"));
    }

    @Test
    void getCurrentUserProfileReturnsPayloadForAuthenticatedUser() throws Exception {
        UserProfileDto response = new UserProfileDto();
        response.setUsername("alice");
        response.setEmail("alice@example.com");
        response.setFirstName("Alice");
        when(currentUserProfileService.getCurrentUserProfile()).thenReturn(response);

        mockMvc.perform(get("/users/me")
                        .header("Authorization", bearerToken("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.firstName").value("Alice"));
    }

    @Test
    void updateCurrentUserProfileMapsConflictTo409() throws Exception {
        UpdateCurrentUserRequest request = new UpdateCurrentUserRequest();
        request.setEmail("taken@example.com");
        request.setFirstName("Alice");
        request.setLastName("Doe");
        when(currentUserProfileService.updateCurrentUserProfile(any(UpdateCurrentUserRequest.class)))
                .thenThrow(new ConflictException("Email already taken"));

        mockMvc.perform(put("/users/me")
                        .header("Authorization", bearerToken("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email already taken"));

        verify(currentUserProfileService).updateCurrentUserProfile(any(UpdateCurrentUserRequest.class));
    }

    private UserDetails authenticatedUser(String username) {
        return User.withUsername(username)
                .password("ignored")
                .authorities(new String[0])
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
            UsersApiController.class,
            UsersApiDelegateImpl.class
    })
    static class TestApplication {
    }
}
