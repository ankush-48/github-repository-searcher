package com.ankush.reposearch.api;

import com.ankush.reposearch.domain.RepositoryRecord;
import java.util.List;

public final class Responses {
  private Responses() {}

  public record SearchResponse(
      String message,
      List<RepositoryRecord> repositories,
      long totalCount,
      boolean incompleteResults,
      int page,
      int perPage) {}

  public record StoredResponse(
      List<RepositoryRecord> repositories,
      long totalElements,
      int page,
      int size,
      long totalPages) {}
}
