-- PostgreSQL: only needed when DDL_AUTO is validate/none.
-- With the project's default DDL_AUTO=update, Hibernate creates these tables.
CREATE TABLE IF NOT EXISTS auth_sessions (
    id varchar(36) PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users(id),
    token_version integer NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_auth_session_user ON auth_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_session_expiry ON auth_sessions(expires_at);
CREATE TABLE IF NOT EXISTS auth_refresh_credentials (
    token_hash varchar(64) PRIMARY KEY,
    session_id varchar(36) NOT NULL REFERENCES auth_sessions(id),
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    request_id varchar(36),
    next_hash varchar(64)
);
CREATE INDEX IF NOT EXISTS idx_auth_refresh_session ON auth_refresh_credentials(session_id);
CREATE INDEX IF NOT EXISTS idx_auth_refresh_expiry ON auth_refresh_credentials(expires_at);
