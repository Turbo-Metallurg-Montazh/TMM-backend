package com.kindred.emkcrm_project_backend.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "search-service")
public record SearchServiceProperties(
        @NotBlank String baseUrl,
        @NotBlank String serviceName,
        @NotBlank String privateKeyPem,
        List<String> scopes,
        long tokenTtlSeconds,
        long tokenRefreshMarginSeconds
) {
}
