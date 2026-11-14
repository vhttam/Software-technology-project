CREATE TABLE listening_events (
    id VARCHAR(36) PRIMARY KEY,
    visitor_id VARCHAR(64) NOT NULL,
    poi_id VARCHAR(36) NOT NULL,
    content_id VARCHAR(36),
    version INT,
    lang VARCHAR(10) NOT NULL,
    audio_id VARCHAR(36),
    event_type VARCHAR(20) NOT NULL,
    listened_seconds INT DEFAULT 0,
    duration_seconds INT DEFAULT 0,
    occurred_at VARCHAR(35) NOT NULL
);

CREATE INDEX idx_listening_occurred_at ON listening_events(occurred_at);
CREATE INDEX idx_listening_lang ON listening_events(lang);
