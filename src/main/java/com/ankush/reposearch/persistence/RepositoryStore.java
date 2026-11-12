package com.ankush.reposearch.persistence;

import com.ankush.reposearch.api.Responses.StoredResponse;
import com.ankush.reposearch.domain.RepositoryRecord;
import com.ankush.reposearch.domain.SortKey;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
public class RepositoryStore {
  private static final String COLUMNS =
      "id, name, description, owner, language, stars, forks, last_updated, html_url";
  private static final RowMapper<RepositoryRecord> MAPPER =
      (rs, row) ->
          new RepositoryRecord(
              rs.getLong("id"),
              rs.getString("name"),
              rs.getString("description"),
              rs.getString("owner"),
              rs.getString("language"),
              rs.getLong("stars"),
              rs.getLong("forks"),
              rs.getTimestamp("last_updated").toInstant(),
              rs.getString("html_url"));
  private static final String UPSERT =
      """
      INSERT INTO github_repositories (id, name, description, owner, language, stars, forks, last_updated, html_url, fetched_at)
      VALUES (:id, :name, :description, :owner, :language, :stars, :forks, :updated, :url, :fetched)
      ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description,
        owner = EXCLUDED.owner, language = EXCLUDED.language, stars = EXCLUDED.stars,
        forks = EXCLUDED.forks, last_updated = EXCLUDED.last_updated, html_url = EXCLUDED.html_url,
        fetched_at = EXCLUDED.fetched_at
      WHERE EXCLUDED.last_updated > github_repositories.last_updated
         OR (EXCLUDED.last_updated = github_repositories.last_updated AND EXCLUDED.fetched_at >= github_repositories.fetched_at)
      """;
  private final NamedParameterJdbcTemplate jdbc;

  public RepositoryStore(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public List<RepositoryRecord> upsert(List<RepositoryRecord> records, Instant fetchedAt) {
    if (records.isEmpty()) return List.of();
    var batch =
        records.stream()
            .map(
                record ->
                    new MapSqlParameterSource()
                        .addValue("id", record.id())
                        .addValue("name", record.name())
                        .addValue("description", record.description())
                        .addValue("owner", record.owner())
                        .addValue("language", record.language())
                        .addValue("stars", record.stars())
                        .addValue("forks", record.forks())
                        .addValue("updated", Timestamp.from(record.lastUpdated()))
                        .addValue("url", record.htmlUrl())
                        .addValue("fetched", Timestamp.from(fetchedAt)))
            .toArray(SqlParameterSource[]::new);
    jdbc.batchUpdate(UPSERT, batch);
    var saved =
        jdbc
            .query(
                "SELECT " + COLUMNS + " FROM github_repositories WHERE id IN (:ids)",
                Map.of("ids", records.stream().map(RepositoryRecord::id).toList()),
                MAPPER)
            .stream()
            .collect(Collectors.toMap(RepositoryRecord::id, Function.identity()));
    return records.stream().map(record -> saved.get(record.id())).toList();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public StoredResponse find(String language, Long minStars, SortKey sort, int page, int size) {
    var parameters = new MapSqlParameterSource();
    String where = " WHERE 1=1";
    if (language != null && !language.isBlank()) {
      where += " AND LOWER(language) = LOWER(:language)";
      parameters.addValue("language", language.strip());
    }
    if (minStars != null) {
      where += " AND stars >= :minStars";
      parameters.addValue("minStars", minStars);
    }
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM github_repositories" + where, parameters, Long.class);
    parameters.addValue("limit", size).addValue("offset", (long) page * size);
    // Only a closed enum supplies the SQL identifier; all user values are bound parameters.
    var records =
        jdbc.query(
            "SELECT "
                + COLUMNS
                + " FROM github_repositories"
                + where
                + " ORDER BY "
                + sort.column()
                + " DESC, id ASC LIMIT :limit OFFSET :offset",
            parameters,
            MAPPER);
    long count = total == null ? 0 : total;
    return new StoredResponse(records, count, page, size, (count + size - 1) / size);
  }
}
