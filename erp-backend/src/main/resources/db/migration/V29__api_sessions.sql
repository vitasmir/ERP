CREATE TABLE api_sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES system_users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL
);
CREATE INDEX api_sessions_expiration ON api_sessions(expires_at);

ALTER TABLE invoice_events ADD COLUMN actor VARCHAR(160) NOT NULL DEFAULT 'migration';