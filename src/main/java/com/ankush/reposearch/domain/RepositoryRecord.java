package com.ankush.reposearch.domain;

import java.time.Instant;

public record RepositoryRecord(
    long id,
    String name,
    String description,
    String owner,
    String language,
    long stars,
    long forks,
    Instant lastUpdated,
    String htmlUrl) {}
