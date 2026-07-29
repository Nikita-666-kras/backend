ALTER TABLE auth_users
    ADD COLUMN access_token_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;

CREATE TABLE auth_rate_buckets (
    bucket_key VARCHAR(120) PRIMARY KEY,
    window_start_epoch BIGINT NOT NULL,
    request_count INT NOT NULL
);
