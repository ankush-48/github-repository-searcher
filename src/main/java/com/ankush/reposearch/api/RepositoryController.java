package com.ankush.reposearch.api;

import com.ankush.reposearch.api.Responses.*;
import com.ankush.reposearch.service.RepositoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/github")
@Tag(name = "Repositories", description = "Search GitHub and query persisted repository snapshots")
public class RepositoryController {
  private final RepositoryService service;

  public RepositoryController(RepositoryService service) {
    this.service = service;
  }

  @PostMapping("/search")
  @Operation(summary = "Search GitHub and atomically save one page of results")
  public SearchResponse search(@Valid @RequestBody SearchRequest request) {
    return service.search(request);
  }

  @GetMapping("/repositories")
  @Operation(summary = "Filter and sort stored results without calling GitHub")
  public StoredResponse find(
      @RequestParam(required = false) @Size(max = 50) String language,
      @RequestParam(required = false) @Min(0) Long minStars,
      @RequestParam(defaultValue = "stars") @Pattern(regexp = "stars|forks|updated") String sort,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.find(language, minStars, sort, page, size);
  }
}
