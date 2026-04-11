package com.kindred.emkcrm_project_backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kindred.emkcrm_project_backend.api.AuthApiController;
import com.kindred.emkcrm_project_backend.authentication.JwtTokenProvider;
import com.kindred.emkcrm_project_backend.authentication.PasswordResetService;
import com.kindred.emkcrm_project_backend.authentication.SecurityConfig;
import com.kindred.emkcrm_project_backend.authentication.UserDetail;
import com.kindred.emkcrm_project_backend.authentication.UserService;
import com.kindred.emkcrm_project_backend.authentication.impl.AuthApiDelegateImpl;
import com.kindred.emkcrm_project_backend.config.JacksonConfig;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.exception.AccountDisabledException;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.GlobalExceptionHandler;
import com.kindred.emkcrm_project_backend.exception.UnauthorizedException;
import com.kindred.emkcrm_project_backend.model.LoginRequest;
import com.kindred.emkcrm_project_backend.model.PasswordResetConfirmRequest;
import com.kindred.emkcrm_project_backend.model.TokenResponse;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = AuthApiIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "security.jwt.token.secret-key=12345678901234567890123456789012"
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthApiIntegrationTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper;

    private final JwtTokenProvider jwtTokenProvider;

    private final WebApplicationContext webApplicationContext;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private UserDetail userDetail;

    AuthApiIntegrationTest(
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
    }

    @Test
    void loginReturnsTokenWithoutAuthentication() throws Exception {
        User user = new User();
        user.setUsername("alice");
        when(userService.validateUsername(any(LoginRequest.class))).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");

        String content = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        TokenResponse response = objectMapper.readValue(content, TokenResponse.class);
        org.assertj.core.api.Assertions.assertThat(jwtTokenProvider.getUsernameFromJWT(response.getToken())).isEqualTo("alice");
    }

    @Test
    void loginMapsUnauthorizedTo401() throws Exception {
        when(userService.validateUsername(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Bad login or password"));

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong");

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Bad login or password"));
    }

    @Test
    void loginMapsDisabledAccountTo423() throws Exception {
        when(userService.validateUsername(any(LoginRequest.class)))
                .thenThrow(new AccountDisabledException("User account is disabled"));

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("User account is disabled"));
    }

    @Test
    void confirmPasswordResetIsAvailableWithoutAuthentication() throws Exception {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest();
        request.setToken("reset-token");
        request.setNewPassword("new-password");

        mockMvc.perform(post("/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Пароль успешно обновлен"));

        verify(passwordResetService).confirmPasswordReset("reset-token", "new-password");
    }

    @Test
    void confirmPasswordResetMapsBadRequestTo400() throws Exception {
        doThrow(new BadRequestException("Ссылка сброса пароля недействительна или истекла"))
                .when(passwordResetService)
                .confirmPasswordReset(anyString(), anyString());

        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest();
        request.setToken("bad-token");
        request.setNewPassword("new-password");

        mockMvc.perform(post("/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Ссылка сброса пароля недействительна или истекла"));
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
            AuthApiController.class,
            AuthApiDelegateImpl.class
    })
    static class TestApplication {
    }
}
