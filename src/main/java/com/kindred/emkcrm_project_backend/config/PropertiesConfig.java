package com.kindred.emkcrm_project_backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ActivationProperties.class,
        EmailProperties.class,
        KonturApiProperties.class,
        PaginationProperties.class,
        FormatProperties.class,
        PublicAiProperties.class,
        SearchServiceProperties.class
})
public class PropertiesConfig {}
