package com.ankush.reposearch.persistence;

import static org.assertj.core.api.Assertions.*;

import com.ankush.reposearch.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("integration")
class RepositoryStoreIT {
  @Autowired RepositoryStore store;
  @Autowired JdbcTemplate jdbc;
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @BeforeEach
  void clean() {
    jdbc.execute("TRUNCATE github_repositories");
  }

  @Test
  void insertsAndUpdatesWithoutDuplicates() {
    store.upsert(List.of(record(1, "Java", 10, NOW)), NOW);
    store.upsert(List.of(record(1, "Java", 99, NOW.plusSeconds(1))), NOW.plusSeconds(2));
    var result = store.find(null, null, SortKey.stars, 0, 20);
    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.repositories().getFirst().stars()).isEqualTo(99);
  }

  @Test
  void olderResponsesCannotOverwriteNewerData() {
    store.upsert(List.of(record(1, "Java", 100, NOW)), NOW.plusSeconds(5));
    var returned =
        store.upsert(List.of(record(1, "Java", 5, NOW.minusSeconds(1))), NOW.plusSeconds(10));
    assertThat(returned.getFirst().stars()).isEqualTo(100);
    store.upsert(List.of(record(1, "Java", 1, NOW)), NOW);
    assertThat(store.find(null, null, SortKey.stars, 0, 20).repositories().getFirst().stars())
        .isEqualTo(100);
  }

  @Test
  void filtersCaseInsensitivelyAndPaginatesWithStableTies() {
    store.upsert(
        List.of(
            record(3, "Java", 20, NOW),
            record(1, "Java", 20, NOW),
            record(2, "Python", 50, NOW),
            record(4, null, 1, NOW)),
        NOW);
    var page = store.find("java", 10L, SortKey.stars, 1, 1);
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.totalPages()).isEqualTo(2);
    assertThat(page.repositories()).extracting(RepositoryRecord::id).containsExactly(3L);
    assertThat(store.find(null, 100L, SortKey.stars, 0, 20).repositories()).isEmpty();
    assertThat(store.find(null, null, SortKey.stars, 100, 20).repositories()).isEmpty();
  }

  @Test
  void sortsForksAndUpdated() {
    store.upsert(
        List.of(record(1, "Java", 10, NOW), record(2, "Java", 1, NOW.plusSeconds(5))), NOW);
    assertThat(store.find(null, null, SortKey.forks, 0, 20).repositories())
        .extracting(RepositoryRecord::id)
        .containsExactly(1L, 2L);
    assertThat(store.find(null, null, SortKey.updated, 0, 20).repositories())
        .extracting(RepositoryRecord::id)
        .containsExactly(2L, 1L);
  }

  @Test
  void supportsNullFieldsAndEmptyBatch() {
    assertThat(store.upsert(List.of(), NOW)).isEmpty();
    store.upsert(List.of(record(1, null, 0, NOW)), NOW);
    assertThat(store.find(null, null, SortKey.stars, 0, 20).repositories().getFirst().language())
        .isNull();
  }

  @Test
  void rollsBackEntireBatchOnConstraintViolation() {
    assertThatThrownBy(
            () -> store.upsert(List.of(record(1, "Java", 5, NOW), record(2, "Java", -1, NOW)), NOW))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM github_repositories", Long.class))
        .isZero();
  }

  @Test
  void concurrentUpsertsRemainUniqueAndKeepNewestSnapshot() throws Exception {
    try (var executor = Executors.newFixedThreadPool(4)) {
      var tasks =
          java.util.stream.IntStream.range(0, 8)
              .<Callable<Void>>mapToObj(
                  index ->
                      () -> {
                        store.upsert(
                            List.of(record(1, "Java", index, NOW.plusSeconds(index))),
                            NOW.plusSeconds(index));
                        return null;
                      })
              .toList();
      for (var future : executor.invokeAll(tasks)) future.get(15, TimeUnit.SECONDS);
    }
    var records = store.find(null, null, SortKey.stars, 0, 20);
    assertThat(records.totalElements()).isEqualTo(1);
    assertThat(records.repositories().getFirst().stars()).isEqualTo(7);
  }

  @Test
  void treatsInjectionAsData() {
    store.upsert(List.of(record(1, "Java", 10, NOW)), NOW);
    assertThat(store.find("Java' OR 1=1 --", null, SortKey.stars, 0, 20).repositories()).isEmpty();
    assertThat(store.find(null, null, SortKey.stars, 0, 20).totalElements()).isEqualTo(1);
  }

  private RepositoryRecord record(long id, String language, long stars, Instant updated) {
    return new RepositoryRecord(
        id,
        "repo-" + id,
        null,
        "team",
        language,
        stars,
        stars,
        updated,
        "https://github.com/team/repo-" + id);
  }
}
