package com.kindred.emkcrm_project_backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kindred.emkcrm_project_backend.api.TenderFiltersApiController;
import com.kindred.emkcrm_project_backend.authentication.JwtTokenProvider;
import com.kindred.emkcrm_project_backend.authentication.SecurityConfig;
import com.kindred.emkcrm_project_backend.authentication.UserDetail;
import com.kindred.emkcrm_project_backend.config.JacksonConfig;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.ConflictException;
import com.kindred.emkcrm_project_backend.exception.GlobalExceptionHandler;
import com.kindred.emkcrm_project_backend.model.ExportTendersByFilterRequest;
import com.kindred.emkcrm_project_backend.model.ExportTendersByFilterResponse;
import com.kindred.emkcrm_project_backend.model.FoundTendersResponse;
import com.kindred.emkcrm_project_backend.model.ParseTenderFilterRequest;
import com.kindred.emkcrm_project_backend.model.TenderFilterSummaryResponse;
import com.kindred.emkcrm_project_backend.services.TenderFilterExportService;
import com.kindred.emkcrm_project_backend.services.TenderFilterManagementService;
import com.kindred.emkcrm_project_backend.tenderfilters.TenderFiltersApiDelegateImpl;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = TenderFiltersApiIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "security.jwt.token.secret-key=12345678901234567890123456789012"
)
class TenderFiltersApiIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private TenderFilterManagementService tenderFilterManagementService;

    @MockitoBean
    private TenderFilterExportService tenderFilterExportService;

    @MockitoBean
    private UserDetail userDetail;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(webApplicationContext.getBean("springSecurityFilterChain", Filter.class))
                .build();

        UserDetails principal = User.withUsername("alice")
                .password("ignored")
                .authorities("TENDER_FILTER.WRITE")
                .build();
        when(userDetail.loadUserByUsername("alice")).thenReturn(principal);
    }

    @Test
    void parseTenderFilterRequiresAuthentication() throws Exception {
        ParseTenderFilterRequest request = new ParseTenderFilterRequest();
        request.setFilter("https://zakupki.kontur.ru/Grid?q.Text=test");

        mockMvc.perform(post("/tender-filters/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Unauthorized\"}"));
    }

    @Test
    void parseTenderFilterReturnsCreatedNameWhenAuthorized() throws Exception {
        ParseTenderFilterRequest request = new ParseTenderFilterRequest();
        request.setName(" ");
        request.setFilter("https://zakupki.kontur.ru/Grid?q.Text=test");
        when(tenderFilterManagementService.parseTenderFilter(any(ParseTenderFilterRequest.class)))
                .thenReturn("generated-filter");

        mockMvc.perform(post("/tender-filters/parse")
                        .header("Authorization", bearerToken("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("generated-filter"));

        verify(tenderFilterManagementService).parseTenderFilter(any(ParseTenderFilterRequest.class));
    }

    @Test
    void parseTenderFilterMapsConflictTo409() throws Exception {
        ParseTenderFilterRequest request = new ParseTenderFilterRequest();
        request.setFilter("https://zakupki.kontur.ru/Grid?q.Text=test");
        when(tenderFilterManagementService.parseTenderFilter(any(ParseTenderFilterRequest.class)))
                .thenThrow(new ConflictException("duplicate filter"));

        mockMvc.perform(post("/tender-filters/parse")
                        .header("Authorization", bearerToken("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate filter"));
    }

    @Test
    void listTenderFiltersReturnsJsonArrayForAuthorizedUser() throws Exception {
        TenderFilterSummaryResponse first = new TenderFilterSummaryResponse();
        first.setName("A");
        first.setActive(true);
        TenderFilterSummaryResponse second = new TenderFilterSummaryResponse();
        second.setName("B");
        second.setActive(false);
        when(tenderFilterManagementService.listTenderFilters()).thenReturn(List.of(first, second));

        mockMvc.perform(get("/tender-filters")
                        .header("Authorization", bearerToken("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("A"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].name").value("B"))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    void exportTendersByFilterReturnsPayload() throws Exception {
        ExportTendersByFilterRequest request = new ExportTendersByFilterRequest();
        request.setFilterName("demo-filter");

        ExportTendersByFilterResponse response = new ExportTendersByFilterResponse();
        response.setFilterName("demo-filter");
        response.setLoadedPagesCount(1);
        FoundTendersResponse foundTendersResponse = new FoundTendersResponse();
        foundTendersResponse.setTotalCount(0);
        response.setFoundTenders(foundTendersResponse);
        when(tenderFilterExportService.exportTendersByFilter(any(ExportTendersByFilterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/tender-filters/export")
                        .header("Authorization", bearerToken("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filterName").value("demo-filter"))
                .andExpect(jsonPath("$.loadedPagesCount").value(1))
                .andExpect(jsonPath("$.foundTenders.totalCount").value(0));
    }

    @Test
    void exportTendersByFilterMapsBadRequestTo400() throws Exception {
        ExportTendersByFilterRequest request = new ExportTendersByFilterRequest();
        request.setFilterName("demo-filter");
        doThrow(new BadRequestException("date range is invalid"))
                .when(tenderFilterExportService)
                .exportTendersByFilter(any(ExportTendersByFilterRequest.class));

        mockMvc.perform(post("/tender-filters/export")
                        .header("Authorization", bearerToken("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("date range is invalid"));
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
            TenderFiltersApiController.class,
            TenderFiltersApiDelegateImpl.class
    })
    static class TestApplication {
    }
}
