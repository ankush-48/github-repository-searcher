package com.ankush.reposearch.service;

import com.ankush.reposearch.api.Responses.*;
import com.ankush.reposearch.api.SearchRequest;
import com.ankush.reposearch.domain.SortKey;
import com.ankush.reposearch.error.ApiException;
import com.ankush.reposearch.github.GitHubClient;
import com.ankush.reposearch.persistence.RepositoryStore;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RepositoryService {
  private final GitHubClient github;
  private final RepositoryStore store;
  private final Clock clock;

  public RepositoryService(GitHubClient github, RepositoryStore store, Clock clock) {
    this.github = github;
    this.store = store;
    this.clock = clock;
  }

  public SearchResponse search(SearchRequest request) {
    if ((long) (request.page() - 1) * request.perPage() >= 1000) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "SEARCH_WINDOW_EXCEEDED",
          "GitHub search exposes only the first 1000 results");
    }
    var fetchedAt = clock.instant();
    // No database transaction is held open during the network call.
    var result = github.search(request);
    var records = store.upsert(result.repositories(), fetchedAt);
    return new SearchResponse(
        "Repositories fetched and saved successfully",
        records,
        result.totalCount(),
        result.incompleteResults(),
        request.page(),
        request.perPage());
  }

  public StoredResponse find(String language, Long minStars, String sort, int page, int size) {
    return store.find(language, minStars, SortKey.valueOf(sort), page, size);
  }
}
