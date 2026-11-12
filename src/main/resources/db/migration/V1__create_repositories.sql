CREATE TABLE github_repositories (
    id BIGINT PRIMARY KEY CHECK (id > 0),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner VARCHAR(255) NOT NULL,
    language VARCHAR(100),
    stars BIGINT NOT NULL CHECK (stars >= 0),
    forks BIGINT NOT NULL CHECK (forks >= 0),
    last_updated TIMESTAMPTZ NOT NULL,
    html_url TEXT NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_repositories_stars ON github_repositories (stars DESC, id ASC);
CREATE INDEX idx_repositories_forks ON github_repositories (forks DESC, id ASC);
CREATE INDEX idx_repositories_updated ON github_repositories (last_updated DESC, id ASC);
CREATE INDEX idx_repositories_language_stars ON github_repositories (LOWER(language), stars DESC, id ASC);
