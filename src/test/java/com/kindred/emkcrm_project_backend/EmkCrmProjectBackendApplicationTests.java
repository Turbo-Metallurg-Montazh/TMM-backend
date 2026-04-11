package com.kindred.emkcrm_project_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@SpringBootTest(
        classes = EmkCrmProjectBackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "security.jwt.token.secret-key=12345678901234567890123456789012",
        "spring.jpa.open-in-view=false"
})
class EmkCrmProjectBackendApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("emk_crm_test")
            .withUsername("emk_crm")
            .withPassword("emk_crm");

    private final DataSource dataSource;

    EmkCrmProjectBackendApplicationTests(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Test
    void contextLoads() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(dataSource).isNotNull();
    }

}
