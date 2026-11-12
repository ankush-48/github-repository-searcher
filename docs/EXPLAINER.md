# Understand the implementation before presenting it

## Explain it in one sentence

“My Spring Boot backend fetches GitHub repository details, saves one canonical row per GitHub ID in PostgreSQL, and provides filtered and sorted access to those saved snapshots.”

## Questions you may be asked

**Why two endpoints?** Search has an external side effect and writes snapshots, so it uses POST. Retrieving saved data is read-only, so it uses GET.

**Why PostgreSQL?** It is required by the assignment and provides constraints, transactions, indexing and atomic upserts.

**Why not just use a list or a map?** The data must persist across application restarts and be safely shared across concurrent requests or service instances. PostgreSQL provides that persistence and coordination.

**Why JDBC instead of JPA?** There is only one table, and native batch upserts are the central operation. JDBC keeps that operation explicit and avoids per-record existence queries. JPA is a valid alternative, not inherently inferior.

**How do duplicates get prevented?** GitHub's repository ID is the primary key. `ON CONFLICT (id) DO UPDATE` atomically updates that row. The primary key, not a Java “does it exist?” check, enforces uniqueness.

**What if two searches finish out of order?** A conditional upsert rejects snapshots with older GitHub update timestamps. When timestamps tie, fetch start time provides a best-effort ordering. GitHub's timestamp is not a perfect version number for every statistic; this limitation is documented.

**Why no transaction around the HTTP call?** It would hold database resources while waiting for a remote service. Fetch and validate first, then use a short transaction for persistence.

**Why validate the upstream response?** A 200 HTTP response does not guarantee complete or valid repository data. Invalid fields are rejected before any records are written.

**How do filtering and sorting stay safe?** Values use bound SQL parameters. The sort column comes from a fixed enum rather than arbitrary user text. Language filtering is case-insensitive.

**How is pagination handled?** GitHub search starts at page 1; local database retrieval starts at page 0. Both have a maximum page size of 100. Search imports one page rather than trying to fetch every match.

**What happens if GitHub is unavailable?** Search returns a safe upstream error. Stored retrieval still works if PostgreSQL is healthy because it does not call GitHub.

**Why return 503 for an upstream rate limit?** The dependency's quota is unavailable; it is not necessarily the calling user's own quota. `Retry-After` tells callers when to try again. There are no automatic retries that could amplify quota usage.

**How do you test database-specific behavior?** The integration tests use a dedicated real PostgreSQL database and apply the same Flyway migration as production. They test native upserts, concurrency and rollback instead of approximating them with another database.

**Is this production-ready?** It is a focused assessment backend with validation, transaction handling, tests and deployment configuration. A public production product would additionally need authentication, ingress rate limiting, retention limits and operational monitoring. Do not claim it is production-hardened.

**What would you improve next?** Cursor pagination for large saved datasets, quotas per caller, metrics for upstream latency, and a deliberate refresh/retention policy. Add features only when requirements justify them.

## Things not to say in the interview

- Do not claim the application searches all of GitHub and stores every result.
- Do not call the API “real-time synchronization”; it stores snapshots when searched.
- Do not claim GitHub data can never change between two requests.
- Do not claim 100% coverage, load testing, deployment success or TDD unless you can show evidence.
- Be comfortable explaining every submitted class. Modify and practice the project before recording so the explanation is your own.
