CREATE TABLE IF NOT EXISTS log_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TEXT NOT NULL,
    last_seen_at TEXT NOT NULL,
    service TEXT NOT NULL,
    category TEXT NOT NULL,
    level TEXT NOT NULL,
    message TEXT NOT NULL,
    details_json TEXT,
    actor_id TEXT,
    actor_username TEXT,
    request_id TEXT,
    fingerprint TEXT NOT NULL UNIQUE,
    count INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_log_events_created_at ON log_events(created_at);
CREATE INDEX IF NOT EXISTS idx_log_events_level_created_at ON log_events(level, created_at);
CREATE INDEX IF NOT EXISTS idx_log_events_category_created_at ON log_events(category, created_at);
