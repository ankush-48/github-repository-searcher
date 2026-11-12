package com.ankush.reposearch.github;

import com.ankush.reposearch.api.SearchRequest;
import com.ankush.reposearch.domain.RepositoryRecord;
import com.ankush.reposearch.error.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class GitHubClient {
  private final RestClient client;
  private final Clock clock;

  public GitHubClient(RestClient gitHubRestClient, Clock clock) {
    this.client = gitHubRestClient;
    this.clock = clock;
  }

  public record SearchPage(
      List<RepositoryRecord> repositories, long totalCount, boolean incompleteResults) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Owner(String login) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Item(
      Long id,
      String name,
      String description,
      Owner owner,
      String language,
      @JsonProperty("stargazers_count") Long stars,
      @JsonProperty("forks_count") Long forks,
      @JsonProperty("updated_at") Instant updated,
      @JsonProperty("html_url") String url) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Payload(
      @JsonProperty("total_count") Long totalCount,
      @JsonProperty("incomplete_results") Boolean incompleteResults,
      List<Item> items) {}

  public SearchPage search(SearchRequest request) {
    String query = request.query() + " in:name";
    if (request.language() != null) query += " language:\"" + request.language() + "\"";
    String finalQuery = query;
    try {
      var response =
          client
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/search/repositories")
                          .queryParam("q", "{query}")
                          .queryParam("sort", request.sort())
                          .queryParam("order", "desc")
                          .queryParam("page", request.page())
                          .queryParam("per_page", request.perPage())
                          .build(finalQuery))
              .retrieve()
              .body(Payload.class);
      if (response == null
          || response.items() == null
          || response.totalCount() == null
          || response.totalCount() < 0
          || response.incompleteResults() == null
          || response.items().size() > request.perPage()) throw invalidResponse();
      var repositories = response.items().stream().map(this::map).toList();
      if (repositories.stream().map(RepositoryRecord::id).distinct().count() != repositories.size())
        throw invalidResponse();
      return new SearchPage(repositories, response.totalCount(), response.incompleteResults());
    } catch (RestClientResponseException exception) {
      throw translate(exception);
    } catch (ResourceAccessException exception) {
      Throwable cause = exception;
      while (cause != null) {
        if (cause instanceof SocketTimeoutException) {
          throw new ApiException(
              HttpStatus.GATEWAY_TIMEOUT, "GITHUB_TIMEOUT", "GitHub did not respond in time");
        }
        cause = cause.getCause();
      }
      throw new ApiException(
          HttpStatus.BAD_GATEWAY, "GITHUB_UNAVAILABLE", "Could not connect to GitHub");
    } catch (RestClientException exception) {
      throw invalidResponse();
    }
  }

  private RepositoryRecord map(Item item) {
    if (item == null
        || item.id() == null
        || item.id() <= 0
        || item.name() == null
        || item.name().isBlank()
        || item.name().length() > 255
        || item.owner() == null
        || item.owner().login() == null
        || item.owner().login().isBlank()
        || item.owner().login().length() > 255
        || (item.language() != null && item.language().length() > 100)
        || item.stars() == null
        || item.stars() < 0
        || item.forks() == null
        || item.forks() < 0
        || item.updated() == null
        || item.url() == null
        || !item.url().startsWith("https://github.com/")) {
      throw invalidResponse();
    }
    return new RepositoryRecord(
        item.id(),
        item.name(),
        item.description(),
        item.owner().login(),
        item.language(),
        item.stars(),
        item.forks(),
        item.updated(),
        item.url());
  }

  private ApiException translate(RestClientResponseException exception) {
    int status = exception.getStatusCode().value();
    HttpHeaders headers = exception.getResponseHeaders();
    boolean rateLimited =
        status == 429
            || (status == 403
                && headers != null
                && ("0".equals(headers.getFirst("X-RateLimit-Remaining"))
                    || headers.containsKey(HttpHeaders.RETRY_AFTER)));
    if (rateLimited)
      return new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "GITHUB_RATE_LIMITED",
          "GitHub search rate limit reached; retry later",
          retryAfter(headers));
    if (status == 422)
      return new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "GITHUB_QUERY_REJECTED",
          "GitHub rejected the search query or pagination range");
    return new ApiException(
        HttpStatus.BAD_GATEWAY,
        "GITHUB_UPSTREAM_ERROR",
        "GitHub returned an unsuccessful response");
  }

  private long retryAfter(HttpHeaders headers) {
    if (headers == null) return 60;
    String retry = headers.getFirst(HttpHeaders.RETRY_AFTER);
    if (retry != null) {
      try {
        return Math.max(1, Math.min(86400, Long.parseLong(retry)));
      } catch (NumberFormatException ignored) {
        try {
          return Math.max(
              1,
              Math.min(
                  86400,
                  ZonedDateTime.parse(retry, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
                      - clock.instant().getEpochSecond()));
        } catch (RuntimeException invalidDate) {
          /* Fall back to the rate-limit reset header. */
        }
      }
    }
    try {
      return Math.max(
          1,
          Math.min(
              86400,
              Long.parseLong(headers.getFirst("X-RateLimit-Reset"))
                  - clock.instant().getEpochSecond()));
    } catch (NumberFormatException ignored) {
      return 60;
    }
  }

  private ApiException invalidResponse() {
    return new ApiException(
        HttpStatus.BAD_GATEWAY, "GITHUB_INVALID_RESPONSE", "GitHub returned an invalid response");
  }
}
