package com.ankush.reposearch.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ankush.reposearch.api.SearchRequest;
import com.ankush.reposearch.domain.*;
import com.ankush.reposearch.error.ApiException;
import com.ankush.reposearch.github.GitHubClient;
import com.ankush.reposearch.persistence.RepositoryStore;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;

class RepositoryServiceTest {
  private final GitHubClient client = mock(GitHubClient.class);
  private final RepositoryStore store = mock(RepositoryStore.class);
  private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
  private final RepositoryService service =
      new RepositoryService(client, store, Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void searchesAndSavesUsingCanonicalStoredData() {
    var request = new SearchRequest("spring", null, null, null, null);
    var records =
        List.of(
            new RepositoryRecord(
                1, "spring", null, "team", "Java", 4, 2, now, "https://github.com/team/spring"));
    when(client.search(request)).thenReturn(new GitHubClient.SearchPage(records, 100, false));
    when(store.upsert(records, now)).thenReturn(records);
    var result = service.search(request);
    assertThat(result.repositories()).isEqualTo(records);
    assertThat(result.totalCount()).isEqualTo(100);
    assertThat(result.page()).isEqualTo(1);
    assertThat(result.perPage()).isEqualTo(30);
    var ordered = inOrder(client, store);
    ordered.verify(client).search(request);
    ordered.verify(store).upsert(records, now);
  }

  @Test
  void rejectsSearchBeyondGitHubWindow() {
    assertThatThrownBy(() -> service.search(new SearchRequest("spring", null, null, 11, 100)))
        .isInstanceOfSatisfying(
            ApiException.class,
            error -> assertThat(error.code()).isEqualTo("SEARCH_WINDOW_EXCEEDED"));
    verifyNoInteractions(client, store);
  }

  @Test
  void upstreamFailureDoesNotTouchDatabase() {
    var request = new SearchRequest("spring", null, null, null, null);
    when(client.search(request))
        .thenThrow(
            new ApiException(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "UPSTREAM", "failed"));
    assertThatThrownBy(() -> service.search(request)).isInstanceOf(ApiException.class);
    verifyNoInteractions(store);
  }

  @Test
  void retrievalDoesNotCallGitHub() {
    service.find("Java", 5L, "updated", 0, 20);
    verify(store).find("Java", 5L, SortKey.updated, 0, 20);
    verifyNoInteractions(client);
  }

  @Test
  void normalizesDefaults() {
    var request = new SearchRequest("  spring  ", "  ", null, null, null);
    assertThat(request.query()).isEqualTo("spring");
    assertThat(request.language()).isNull();
    assertThat(request.sort()).isEqualTo("stars");
  }
}
