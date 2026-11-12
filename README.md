# GitHub Repository Searcher

A Java 21 / Spring Boot REST API that searches GitHub repositories, stores snapshots in PostgreSQL, and lets callers filter and sort saved results without making another GitHub request.

Built for the **GitHub Repository Searcher backend assignment**. There is no custom frontend; use Postman or the generated Swagger documentation.

## Run with Docker

```bash
git clone https://github.com/ankush-48/github-repository-searcher.git
cd github-repository-searcher
docker compose up --build -d
```

Open:

- Health: <http://localhost:8080/actuator/health>
- Swagger: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

The database uses a persistent Docker volume. The local defaults are in `.env.example`; copy it to `.env` if you want to override them. A GitHub token is optional and must remain private. Stop services with `docker compose down`.

## API

### Search and persist

`POST /api/github/search`

```json
{
  "query": "spring-boot",
  "language": "Java",
  "sort": "stars",
  "page": 1,
  "perPage": 5
}
```

`query` is required (1–200 characters after trimming): repository name terms, using letters, numbers, spaces, `.`, `_`, `/` or `-`. GitHub's search semantics apply; this is not an arbitrary substring matcher. Searches are scoped to repository names with `in:name`. Language is optional; names such as `C++` are safely URI encoded.

`sort`: `stars` (default), `forks`, or `updated`, descending. `page` starts at **1** and `perPage` defaults to 30, maximum 100. GitHub only exposes the first 1000 search results; requests beyond that window are rejected. Only the requested page is saved. An incomplete final page/window may still be rejected by GitHub; that returns 422.

```json
{
  "message": "Repositories fetched and saved successfully",
  "repositories": [
    {
      "id": 123456,
      "name": "spring-boot-example",
      "description": "Example description",
      "owner": "example-owner",
      "language": "Java",
      "stars": 450,
      "forks": 120,
      "lastUpdated": "2026-01-01T12:00:00Z",
      "htmlUrl": "https://github.com/example-owner/spring-boot-example"
    }
  ],
  "totalCount": 1,
  "incompleteResults": false,
  "page": 1,
  "perPage": 5
}
```

Example data above is illustrative, not a claim about a real repository. Description and language may be null. Repeated searches update existing rows by GitHub ID rather than inserting duplicates. A response is returned only after persistence succeeds.

### Retrieve stored repositories

`GET /api/github/repositories?language=Java&minStars=100&sort=stars&page=0&size=20`

All filters are optional. Language matches case-insensitively; `minStars` must be non-negative. `sort` accepts `stars`, `forks`, or `updated`; descending with ID ascending as a stable tiebreaker. Local `page` starts at **0**, `size` defaults to 20 and is capped at 100.

```json
{"repositories": [], "totalElements": 0, "page": 0, "size": 20, "totalPages": 0}
```

This endpoint reads only PostgreSQL. It can return saved data even when GitHub is unavailable. Empty searches and unmatched filters return HTTP 200 with an empty array.

### Error contract

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/github/search",
  "details": ["query: must not be blank"]
}
```

| Status | Meaning |
|---|---|
| 400 | Invalid input, JSON, sort, pagination or GitHub search window |
| 422 | GitHub rejected the search query/window |
| 502 | Invalid upstream response, upstream failure, connection failure or token rejected upstream |
| 503 | GitHub quota exhausted (`Retry-After` supplied), or database unavailable |
| 504 | GitHub timeout |
| 500 | Unexpected internal failure; implementation details are not exposed |

Unknown JSON fields are rejected to catch misspelled request properties. GitHub's extra response fields are ignored. Upstream rate limiting is returned as 503 because it is a dependency quota, not necessarily a per-caller API quota. No automatic retries are performed.

## Local development without Docker for the application

Requirements: JDK 21, PostgreSQL 17, and Maven (or the included wrapper).

```bash
docker compose up db -d
./mvnw spring-boot:run
```

Windows: use `mvnw.cmd` instead of `./mvnw`. Environment variables:

| Variable | Default / purpose |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/reposearch` |
| `DB_USERNAME` / `DB_PASSWORD` | `reposearch` / `reposearch` for local development |
| `GITHUB_TOKEN` | Optional server-side token; no default token |
| `GITHUB_API_BASE_URL` | `https://api.github.com`; override only for trusted testing |
| `PORT` | `8080` |

Flyway creates and versions the database schema. JDBC parameters and an enum allowlist prevent query values and sort keys from being interpolated into executable SQL. Bounded HTTP timeouts are configured in `application.yml`.

## Tests

Unit and MVC tests do not require PostgreSQL or GitHub:

```bash
./mvnw test
```

Full verification against a **dedicated disposable PostgreSQL test database**:

```bash
docker compose -f compose.test.yml up -d --wait
./mvnw verify
docker compose -f compose.test.yml down
```

Integration tests truncate `github_repositories` in the configured test database. Never point `TEST_DB_URL` at production. Default: `jdbc:postgresql://localhost:55432/reposearch_test`, user/password `reposearch`. Overrides: `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`.

Tests cover upstream responses, nullable fields, URI encoding, timeouts, rate limits, request validation, stable sorting, filters, pagination, concurrent upserts, stale snapshots, SQL injection as data, and transaction rollback. GitHub Actions runs full verification with PostgreSQL and builds the production container. JaCoCo report: `target/site/jacoco/index.html`.

## Submission materials

- [Postman collection](docs/postman/Repository-Searcher.postman_collection.json) and [environment](docs/postman/Local.postman_environment.json)
- [Architecture and tradeoffs](docs/ARCHITECTURE.md)
- [Deployment guide](docs/DEPLOYMENT.md) and Render Blueprint (`render.yaml`)
- [Loom recording script](docs/LOOM_SCRIPT.md)
- [Interview preparation](docs/EXPLAINER.md)

Deployment and recording links should be added here after those external steps are complete. Do not substitute a localhost URL for a public deployed URL.
