package com.kindred.emkcrm_project_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "email")
public record EmailProperties(
        String domain,
        String activation_link,
        String host_email,
        String login_url,
        String password_reset_url
) {
}
