package com.ankush.reposearch.github;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.ankush.reposearch.api.SearchRequest;
import com.ankush.reposearch.error.ApiException;
import java.net.SocketTimeoutException;
import java.time.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubClientTest {
  private MockRestServiceServer server;
  private GitHubClient client;
  private final SearchRequest request = new SearchRequest("spring boot", "C++", "stars", 1, 30);
  private static final String ITEM =
      """
      {"id":1,"name":"spring","description":null,"owner":{"login":"team"},"language":null,
       "stargazers_count":100,"forks_count":10,"updated_at":"2026-01-01T00:00:00Z",
       "html_url":"https://github.com/team/spring","ignored":"safe"}
      """;

  @BeforeEach
  void setup() {
    var builder = RestClient.builder().baseUrl("https://api.github.com");
    server = MockRestServiceServer.bindTo(builder).build();
    client =
        new GitHubClient(
            builder.build(), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @AfterEach
  void verify() {
    server.verify();
  }

  @Test
  void encodesQueryAndMapsNullableFields() {
    server
        .expect(method(HttpMethod.GET))
        .andExpect(queryParam("q", "spring%20boot%20in%3Aname%20language%3A%22C%2B%2B%22"))
        .andRespond(withSuccess(payload(ITEM), MediaType.APPLICATION_JSON));
    var result = client.search(request);
    assertThat(result.totalCount()).isEqualTo(1);
    assertThat(result.incompleteResults()).isFalse();
    assertThat(result.repositories())
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.id()).isEqualTo(1);
              assertThat(record.language()).isNull();
              assertThat(record.description()).isNull();
              assertThat(record.owner()).isEqualTo("team");
            });
  }

  @Test
  void acceptsEmptyResults() {
    server
        .expect(anything())
        .andRespond(
            withSuccess(
                "{\"total_count\":0,\"incomplete_results\":false,\"items\":[]}",
                MediaType.APPLICATION_JSON));
    assertThat(client.search(request).repositories()).isEmpty();
  }

  @Test
  void exposesIncompleteResults() {
    server
        .expect(anything())
        .andRespond(
            withSuccess(payload(ITEM).replace("false", "true"), MediaType.APPLICATION_JSON));
    assertThat(client.search(request).incompleteResults()).isTrue();
  }

  @ParameterizedTest
  @MethodSource("invalidPayloads")
  void rejectsInvalidPayloads(String json) {
    server.expect(anything()).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.search(request))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> assertThat(error.code()).isEqualTo("GITHUB_INVALID_RESPONSE"));
  }

  static Stream<String> invalidPayloads() {
    return Stream.of(
        "{}",
        "null",
        "not-json",
        payload("null"),
        payload(ITEM + "," + ITEM),
        payload(ITEM.replace("\"id\":1", "\"id\":-1")),
        payload(ITEM.replace("\"stargazers_count\":100", "\"stargazers_count\":null")),
        payload(ITEM.replace("\"forks_count\":10", "\"forks_count\":-1")),
        payload(ITEM.replace("\"login\":\"team\"", "\"login\":\"\"")),
        payload(ITEM.replace("https://github.com/", "https://evil.example/")),
        payload(ITEM.replace("2026-01-01T00:00:00Z", "invalid-date")),
        payload(ITEM).replace("\"total_count\":1", "\"total_count\":-1"));
  }

  @ParameterizedTest
  @CsvSource({
    "401,GITHUB_UPSTREAM_ERROR,502",
    "403,GITHUB_UPSTREAM_ERROR,502",
    "422,GITHUB_QUERY_REJECTED,422",
    "500,GITHUB_UPSTREAM_ERROR,502",
    "429,GITHUB_RATE_LIMITED,503"
  })
  void translatesErrors(int status, String code, int expected) {
    server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)));
    assertThatThrownBy(() -> client.search(request))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> {
              assertThat(error.code()).isEqualTo(code);
              assertThat(error.status().value()).isEqualTo(expected);
            });
  }

  @Test
  void handlesPrimaryRateLimit() {
    server
        .expect(anything())
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", "1767225720"));
    assertThatThrownBy(() -> client.search(request))
        .isInstanceOfSatisfying(
            ApiException.class, error -> assertThat(error.retryAfter()).isEqualTo(120));
  }

  @ParameterizedTest
  @CsvSource({"90,90", "-1,1", "999999,86400", "garbage,60", "'Thu, 1 Jan 2026 00:02:00 GMT',120"})
  void handlesRetryAfter(String header, long expected) {
    server
        .expect(anything())
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", header));
    assertThatThrownBy(() -> client.search(request))
        .isInstanceOfSatisfying(
            ApiException.class, error -> assertThat(error.retryAfter()).isEqualTo(expected));
  }

  @Test
  void handlesTimeout() {
    server
        .expect(anything())
        .andRespond(withException(new SocketTimeoutException("private detail")));
    assertThatThrownBy(() -> client.search(request))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> assertThat(error.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
  }

  @Test
  void handlesConnectionFailure() {
    server.expect(anything()).andRespond(withException(new java.io.IOException("private detail")));
    assertThatThrownBy(() -> client.search(request))
        .isInstanceOfSatisfying(
            ApiException.class, error -> assertThat(error.code()).isEqualTo("GITHUB_UNAVAILABLE"));
  }

  private static String payload(String items) {
    return "{\"total_count\":1,\"incomplete_results\":false,\"items\":[" + items + "]}";
  }
}
