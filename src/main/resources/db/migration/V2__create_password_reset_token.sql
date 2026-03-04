CREATE TABLE IF NOT EXISTS password_reset_token
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES user_info (user_id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_user_id
    ON password_reset_token (user_id);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_expires_at
    ON password_reset_token (expires_at);
