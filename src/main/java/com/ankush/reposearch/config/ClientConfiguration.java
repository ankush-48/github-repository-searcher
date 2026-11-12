package com.ankush.reposearch.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  RestClient gitHubRestClient(RestClient.Builder builder, GitHubProperties properties) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    var client =
        builder
            .baseUrl(properties.baseUrl())
            .requestFactory(factory)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .defaultHeader("User-Agent", "github-repository-searcher");
    if (properties.token() != null && !properties.token().isBlank()) {
      client.defaultHeader("Authorization", "Bearer " + properties.token());
    }
    return client.build();
  }
}
