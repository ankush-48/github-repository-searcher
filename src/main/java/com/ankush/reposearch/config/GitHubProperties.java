package com.ankush.reposearch.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("github")
public record GitHubProperties(
    String baseUrl, String token, Duration connectTimeout, Duration readTimeout) {}
