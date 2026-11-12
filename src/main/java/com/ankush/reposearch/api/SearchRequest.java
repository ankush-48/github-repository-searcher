package com.ankush.reposearch.api;

import jakarta.validation.constraints.*;

public record SearchRequest(
    @NotBlank
        @Size(max = 200)
        @Pattern(
            regexp = "[\\p{L}\\p{N} ._/-]+",
            message = "must contain repository name terms only")
        String query,
    @Size(max = 50)
        @Pattern(regexp = "[A-Za-z0-9+#. -]*", message = "contains unsupported characters")
        String language,
    @Pattern(regexp = "stars|forks|updated", message = "must be stars, forks or updated")
        String sort,
    @Min(1) @Max(1000) Integer page,
    @Min(1) @Max(100) Integer perPage) {
  public SearchRequest {
    query = query == null ? null : query.strip();
    language = language == null || language.isBlank() ? null : language.strip();
    sort = sort == null ? "stars" : sort;
    page = page == null ? 1 : page;
    perPage = perPage == null ? 30 : perPage;
  }
}
