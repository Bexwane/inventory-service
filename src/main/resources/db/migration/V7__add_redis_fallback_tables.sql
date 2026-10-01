-- ────────────────────────────────────────────────────────────────────────────
-- V7: Add Redis Fallback Tables
-- These tables act as the persistent System of Record, ensuring no data
-- is lost and no security loopholes occur if the Redis cache restarts.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE token_blacklist (
    jti UUID PRIMARY KEY,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Index for the sweeper job to quickly find expired rows
CREATE INDEX idx_token_blacklist_expires_at ON token_blacklist(expires_at);


CREATE TABLE idempotency_keys (
    key VARCHAR(255) PRIMARY KEY,
    response_payload JSONB,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);


CREATE TABLE reservation_locks (
    task_id VARCHAR(255) NOT NULL,
    sku VARCHAR(255) NOT NULL,
    location_id UUID NOT NULL,
    container_id UUID,
    qty INT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (task_id, sku, location_id)
);

CREATE INDEX idx_reservation_locks_expires_at ON reservation_locks(expires_at);
