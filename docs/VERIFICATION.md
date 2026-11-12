# Verification record

Verified on 12 November 2026.

| Check | Result |
|---|---|
| Local Maven `verify` | Passed |
| Unit, MVC and HTTP-client tests | 62 passed |
| Real PostgreSQL integration tests | 11 passed |
| JaCoCo line coverage | 251 / 254 lines, 98.82% |
| JaCoCo branch coverage | 82 / 106 branches, 77.36% |
| Coverage gate | Passed: minimum 90% lines, 70% branches |
| Postman collection via Newman, local API | 12 requests and 14 assertions passed |
| Real GitHub API smoke test | Search returned and stored 5 repositories |
| Repeat-search smoke test | Stored count remained 5; all IDs unique |
| Maven wrapper, Windows | Downloaded and launched Maven 3.9.11 successfully |
| GitHub Actions | Tests, reports and production Docker build passed |

The [initial passing CI run](https://github.com/ankush-48/github-repository-searcher/actions/runs/43194128362) verifies implementation commit `1cfdbcf6ebdef02789950d7836c45cf621a75817`. Documentation-only follow-ups may have their own runs; check the latest submitted commit before recording.

Local integration testing used JDK 21 and PostgreSQL 17.6. The production container build was verified by GitHub Actions on Linux. No load test or penetration test is claimed. These results demonstrate the checked behavior; an assessment score remains the evaluator's decision.

Generated reports are not committed. Run `./mvnw verify` with the dedicated test database, or download the CI report artifact to inspect them. Automated integration tests use controlled upstream HTTP responses; the separate manual smoke test used the real GitHub API.
