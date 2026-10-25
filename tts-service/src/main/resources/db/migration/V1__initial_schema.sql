CREATE TABLE audios (audio_id VARCHAR(36) PRIMARY KEY, translation_id VARCHAR(36) NOT NULL, voice_id VARCHAR(100) NOT NULL, object_key VARCHAR(500) NOT NULL, duration_seconds INT NOT NULL, size_bytes BIGINT NOT NULL, created_at VARCHAR(35) NOT NULL, CONSTRAINT uq_audio UNIQUE (translation_id, voice_id));
CREATE TABLE cancelled_jobs (job_id VARCHAR(36) PRIMARY KEY, expires_at VARCHAR(35) NOT NULL);
CREATE TABLE event_attempts (event_id VARCHAR(36) PRIMARY KEY, attempts INT NOT NULL DEFAULT 0, updated_at VARCHAR(35) NOT NULL);
