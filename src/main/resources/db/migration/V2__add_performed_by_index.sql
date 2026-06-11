-- Flyway Migration V2
-- Adds a composite index to vastly improve read performance for the "My Activity Logs" endpoint
-- By indexing on (performed_by, occurred_at DESC), the database can find a user's logs
-- and return them fully sorted without doing a Sequential Table Scan or in-memory sort.

CREATE INDEX idx_movement_user_time ON stock_movements(performed_by, occurred_at DESC);
