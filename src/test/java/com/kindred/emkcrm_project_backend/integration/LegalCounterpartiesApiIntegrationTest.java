package com.kindred.emkcrm_project_backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kindred.emkcrm_project_backend.api.LegalCounterpartiesApiController;
import com.kindred.emkcrm_project_backend.authentication.AuthCookieService;
import com.kindred.emkcrm_project_backend.authentication.JwtTokenProvider;
import com.kindred.emkcrm_project_backend.authentication.SecurityConfig;
import com.kindred.emkcrm_project_backend.authentication.UserDetail;
import com.kindred.emkcrm_project_backend.config.JacksonConfig;
import com.kindred.emkcrm_project_backend.exception.GlobalExceptionHandler;
import com.kindred.emkcrm_project_backend.legal.LegalCounterpartiesApiDelegateImpl;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyDetailsResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyPageResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartySummaryResponse;
import com.kindred.emkcrm_project_backend.model.LegalCounterpartyUpsertRequest;
import com.kindred.emkcrm_project_backend.services.LegalCounterpartyService;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = LegalCounterpartiesApiIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "security.jwt.token.secret-key=12345678901234567890123456789012"
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LegalCounterpartiesApiIntegrationTest {

    private static final String CSRF_TOKEN = "test-csrf-token";

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper;

    private final JwtTokenProvider jwtTokenProvider;

    private final WebApplicationContext webApplicationContext;

    @MockitoBean
    private LegalCounterpartyService legalCounterpartyService;

    @MockitoBean
    private UserDetail userDetail;

    LegalCounterpartiesApiIntegrationTest(
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

        UserDetails principal = User.withUsername("lawyer")
                .password("ignored")
                .authorities("CONTRACTOR.REGISTRY.READ", "CONTRACTOR.REGISTRY.WRITE")
                .build();
        when(userDetail.loadUserByUsername("lawyer")).thenReturn(principal);
    }

    @Test
    void searchCounterpartiesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/legal/counterparties"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Unauthorized\"}"));
    }

    @Test
    void searchCounterpartiesReturnsPageWhenAuthorized() throws Exception {
        LegalCounterpartySummaryResponse item = new LegalCounterpartySummaryResponse();
        item.setId(10L);
        item.setCompanyName("ООО Ромашка");
        item.setInn("6671000000");
        item.setRegistryType("CUSTOMER");
        item.setOverallScore(82);
        item.setRiskLevel("LOW");
        item.setWorkProhibited(false);

        LegalCounterpartyPageResponse response = new LegalCounterpartyPageResponse();
        response.setItems(List.of(item));
        response.setPage(0);
        response.setSize(20);
        response.setTotalElements(1L);
        response.setTotalPages(1);
        when(legalCounterpartyService.search(eq("ром"), eq("CUSTOMER"), eq("LOW"), eq(false), eq(0), eq(20)))
                .thenReturn(response);

        mockMvc.perform(get("/legal/counterparties")
                        .with(auth("lawyer"))
                        .param("query", "ром")
                        .param("registryType", "CUSTOMER")
                        .param("riskLevel", "LOW")
                        .param("workProhibited", "false")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[0].companyName").value("ООО Ромашка"))
                .andExpect(jsonPath("$.items[0].overallScore").value(82))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createCounterpartyReturnsCreatedDetails() throws Exception {
        LegalCounterpartyUpsertRequest request = new LegalCounterpartyUpsertRequest();
        request.setCompanyName("ООО Ромашка");
        request.setInn("6671000000");
        request.setRegistryType("CUSTOMER");

        LegalCounterpartyDetailsResponse response = new LegalCounterpartyDetailsResponse();
        response.setId(10L);
        response.setCompanyName("ООО Ромашка");
        response.setInn("6671000000");
        response.setRegistryType("CUSTOMER");
        when(legalCounterpartyService.create(any(LegalCounterpartyUpsertRequest.class))).thenReturn(response);

        mockMvc.perform(post("/legal/counterparties")
                        .with(auth("lawyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.inn").value("6671000000"));

        verify(legalCounterpartyService).create(any(LegalCounterpartyUpsertRequest.class));
    }

    private RequestPostProcessor auth(String username) {
        return request -> {
            request.setCookies(
                    new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, jwtTokenProvider.generateAccessToken(username)),
                    new Cookie(AuthCookieService.CSRF_TOKEN_COOKIE, CSRF_TOKEN)
            );
            request.addHeader(AuthCookieService.CSRF_TOKEN_HEADER, CSRF_TOKEN);
            return request;
        };
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
            LegalCounterpartiesApiController.class,
            LegalCounterpartiesApiDelegateImpl.class
    })
    static class TestApplication {
    }
}
