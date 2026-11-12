package com.ankush.reposearch.api;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
class RepositoryApiIT {
  private static final AtomicInteger calls = new AtomicInteger();
  private static final AtomicInteger stars = new AtomicInteger(100);
  private static final HttpServer upstream = startServer();
  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("github.base-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
  }

  @AfterAll
  static void stopServer() {
    upstream.stop(0);
  }

  @BeforeEach
  void reset() {
    jdbc.execute("TRUNCATE github_repositories");
    calls.set(0);
    stars.set(100);
  }

  @Test
  void searchUpdateAndRetrievalWorkThroughRealHttpAndPostgres() {
    var request = Map.of("query", "spring", "language", "Java", "perPage", 5);
    var first = http.postForEntity("/api/github/search", request, Responses.SearchResponse.class);
    assertThat(first.getStatusCode().value()).isEqualTo(200);
    assertThat(first.getBody().repositories()).hasSize(1);
    assertThat(first.getBody().repositories().getFirst().stars()).isEqualTo(100);
    stars.set(200);
    var second = http.postForEntity("/api/github/search", request, Responses.SearchResponse.class);
    assertThat(second.getBody().repositories().getFirst().stars()).isEqualTo(200);
    assertThat(calls.get()).isEqualTo(2);
    var stored =
        http.getForEntity(
            "/api/github/repositories?language=java&minStars=150&sort=stars",
            Responses.StoredResponse.class);
    assertThat(stored.getStatusCode().value()).isEqualTo(200);
    assertThat(stored.getBody().totalElements()).isEqualTo(1);
    assertThat(stored.getBody().repositories().getFirst().stars()).isEqualTo(200);
    assertThat(calls.get()).as("Stored retrieval must not call GitHub").isEqualTo(2);
  }

  @Test
  void invalidSearchNeverReachesUpstream() {
    assertThat(
            http.postForEntity("/api/github/search", Map.of("query", ""), Map.class)
                .getStatusCode()
                .value())
        .isEqualTo(400);
    assertThat(calls.get()).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM github_repositories", Long.class))
        .isZero();
  }

  @Test
  void operationalDocumentationAndHealthAreAvailable() {
    assertThat(http.getForEntity("/actuator/health", Map.class).getBody())
        .containsEntry("status", "UP");
    assertThat(http.getForEntity("/v3/api-docs", String.class).getBody())
        .contains("/api/github/search", "/api/github/repositories");
  }

  private static HttpServer startServer() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/search/repositories",
          exchange -> {
            calls.incrementAndGet();
            var body =
                ("""
                {"total_count":1,"incomplete_results":false,"items":[{"id":1,"name":"spring",
                "description":null,"owner":{"login":"team"},"language":"Java","stargazers_count":%d,
                "forks_count":10,"updated_at":"2026-01-01T00:00:00Z","html_url":"https://github.com/team/spring"}]}
                """)
                    .formatted(stars.get())
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var stream = exchange.getResponseBody()) {
              stream.write(body);
            }
          });
      server.start();
      return server;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
