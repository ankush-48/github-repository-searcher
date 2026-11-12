# Deployment

The Dockerfile runs the packaged Java 21 application as a non-root user. The platform must provide a PostgreSQL database and environment variables. The application listens on the platform's `PORT` (default 8080).

## Render

1. Connect the GitHub repository to Render and create a PostgreSQL database plus a Docker web service, or use the included `render.yaml` Blueprint.
2. Review the plans and pricing shown in your account before provisioning. Free-service availability and database lifetime depend on Render's current offering; verify before submission.
3. The Blueprint automatically wires database host, port, name, user and password to the app. For a manually created web service, set `DB_URL` to the **JDBC** form of the internal database address:
   `jdbc:postgresql://HOST:5432/reposearch`
   Use the host and database actually assigned by Render. A `postgres://...` URI is not a JDBC URL. Do not include the username or password in this variable.
4. For manual setup, set `DB_USERNAME` and `DB_PASSWORD` from the database credentials. The Blueprint populates them automatically and builds the JDBC address from `DB_HOST`, `DB_PORT` and `DB_NAME`.
5. Leave `GITHUB_TOKEN` empty for unauthenticated public searches, or set your own token privately in the host's secret environment settings. Never put it in GitHub, Postman or the Loom recording.
6. Configure the health path as `/actuator/health`, deploy, and wait for a healthy status. Flyway applies the schema automatically.
7. Verify the public URL using the requests below. Save the final URL in the README and Postman `baseUrl` variable before submitting.

```bash
curl https://YOUR-SERVICE.onrender.com/actuator/health
curl -X POST https://YOUR-SERVICE.onrender.com/api/github/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"spring-boot","language":"Java","sort":"stars","perPage":5}'
curl 'https://YOUR-SERVICE.onrender.com/api/github/repositories?language=Java&minStars=100&sort=stars'
```

A deployed API URL is sufficient for this backend assignment. Use the API path or `/swagger-ui.html`; the root path is not a homepage. Warm a sleeping service before recording. Check that the database is still available on the assessment date.

## Any Docker host

Build with `docker build -t repository-searcher .`, then configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, optionally `GITHUB_TOKEN`, and `PORT`. Use the database provider's recommended TLS settings, for example `?sslmode=require` on the JDBC URL when required. Keep the database private to the service where the provider supports it.

## Submission checks

- Public GitHub repository can be opened in a signed-out browser.
- Latest CI run is green, including PostgreSQL integration tests.
- Public health endpoint returns `UP` and both API endpoints work.
- README identifies the deployed API URL and Loom URL after you record.
- Postman collection uses the deployed base URL and contains no secrets.
- Review your hosting account for the selected plan, expiry and usage limits.
