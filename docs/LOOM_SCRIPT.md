# Loom recording script

Repository: https://github.com/ankush-48/github-repository-searcher

Live API: https://github-repository-searcher-symh.onrender.com

Swagger: https://github-repository-searcher-symh.onrender.com/swagger-ui.html

Target length: **5–6 minutes**. Speak naturally; do not read every line of code. Use the deployed URL if available. If recording locally, label it honestly and provide the deployed URL separately before submission.

## Prepare before pressing Record

1. Open the repository README, the API client and the latest passing CI run.
2. Import the collection and `Deployed.postman_environment.json` from `docs/postman`, then select the deployed environment. It already contains the public API URL.
3. Send Health once to wake the service. Run Search once to confirm GitHub access before recording.
4. Keep the search small (`perPage: 5`) so the response is readable and does not consume unnecessary quota.
5. Open `RepositoryService.java`, `RepositoryStore.java`, and the test reports in separate tabs.
6. Close any tabs or terminals containing passwords, tokens, personal messages or hosting credentials.
7. Do not claim a specific coverage number or a passing deployment unless the actual result is visible.

## 0:00–0:30 — Introduction

**Show:** GitHub README.

**Say:**

“Hi, I'm Ankush. For this assessment, I built the GitHub Repository Searcher using Java 21, Spring Boot and PostgreSQL. It has two main operations: searching GitHub and saving repository details, and querying the saved repositories using filters and sorting. The submission includes automated tests, Docker setup, API documentation and a Postman collection.”

## 0:30–1:20 — Search and save

**Show:** Postman request **Search and save**. Send:

```json
{"query":"spring-boot","language":"Java","sort":"stars","page":1,"perPage":5}
```

**Say:**

“This POST endpoint searches repository names on GitHub. Language and sorting are configurable. The response contains the repository's GitHub ID, owner, language, stars, forks and last update time. Before returning success, the application saves this page of results in PostgreSQL. Pagination keeps both the upstream request and the database write bounded.”

**Point at:** HTTP 200, repositories, `totalCount`, `incompleteResults`, `page` and `perPage`.

“The total count describes the GitHub search, while the array contains only the requested page. The incomplete-results flag is preserved rather than hidden.”

## 1:20–2:10 — Stored retrieval and duplicate prevention

**Show:** **Stored repositories** with `language=Java`, `minStars=100`, `sort=stars`, `page=0`, `size=20`.

**Say:**

“This GET endpoint queries the database only; it does not call GitHub again. Here I'm filtering Java repositories with at least 100 stars. Results are sorted in descending order, with the repository ID as a stable tiebreaker.”

**Show:** **All stored before repeat**, then **Repeat same search**, then **All stored after repeat**.

“Now I'll repeat the same search. GitHub IDs are primary keys, and a PostgreSQL upsert updates existing records instead of creating duplicates. The Postman check compares the stored IDs and count before and after. For this demonstration, use the same search and avoid other concurrent imports.”

**Do not claim:** that counts can never change between searches. GitHub's live results can change, or another client can import additional repositories.

## 2:10–2:45 — Validation

**Show:** **Invalid search** and **Invalid sort**.

**Say:**

“An empty query returns a structured 400 response with validation details. Unsupported sorting is also rejected. This prevents invalid sort values from reaching the SQL layer. Errors use a consistent format and do not expose SQL, credentials or raw GitHub error bodies.”

“Upstream failures and rate limits are covered by deterministic HTTP tests. When GitHub supplies rate-limit timing, the API returns a Retry-After header. I won't deliberately exhaust the live API during this demo.”

## 2:45–3:45 — Architecture and database decisions

**Show:** architecture diagram, then service and store.

**Say:**

“The controller handles the HTTP contract and validation. The service coordinates the search. A separate GitHub client handles the external API, timeouts and response validation. The repository layer handles persistence.”

“I chose JDBC because PostgreSQL's native ON CONFLICT operation expresses the required update behavior directly. It avoids checking whether each repository exists before writing it, which also avoids a race under concurrent requests.”

**Point at:** `@Transactional`, `batchUpdate` and freshness condition.

“Each page is saved in one transaction. If a record violates a database constraint, the whole batch rolls back. The network request happens before the transaction, so a slow GitHub response doesn't hold a database connection. Older snapshots are prevented from overwriting newer ones using the repository update timestamp and fetch time.”

## 3:45–4:35 — Tests

**Show:** latest passing Actions run or the completed local verification report. Do not wait through a fresh build on video.

**Say:**

“Tests cover request validation, mapping nullable fields, upstream failures, rate limits and timeouts. Integration tests run against real PostgreSQL, so they exercise the actual upsert SQL, filtering, pagination, rollback and concurrent writers. Automated tests mock GitHub's HTTP transport, so they don't depend on GitHub availability or consume live API quota.”

“The CI workflow runs the full verification suite with PostgreSQL and then builds the Docker image.”

**Only say the following after observing green CI:** “This is the passing workflow for the submitted commit.”

## 4:35–5:15 — Deployment and close

**Show:** public `/actuator/health`, Swagger or the deployed API response, then README.

**Say, if deployment is complete:**

“The API is deployed at the URL included with my submission. This health endpoint checks application and database availability. Swagger documents the endpoints, and the repository includes a Postman collection, environment configuration and setup instructions. Docker Compose provides a reproducible local setup.”

**Say:**

“The main tradeoffs are that stored repositories are snapshots, searches import one page at a time, and GitHub limits the accessible search window. For a production service, I would add authentication, request throttling and a data retention policy. For this assessment, I kept the implementation focused on the required behavior, correctness and maintainability. Thank you.”

## If you need a shorter recording

Use about four minutes: introduction (20 seconds), search (45), saved filtering and repeat search (60), validation (25), architecture (50), tests and deployment (40). Keep one clear example per feature.
