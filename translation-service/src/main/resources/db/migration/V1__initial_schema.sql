CREATE TABLE translations (translation_id VARCHAR(36) PRIMARY KEY, content_id VARCHAR(36) NOT NULL, version INT NOT NULL, lang VARCHAR(20) NOT NULL, text_content VARCHAR(MAX) NOT NULL, created_at VARCHAR(35) NOT NULL, CONSTRAINT uq_translation UNIQUE (content_id, version, lang));
CREATE TABLE cancelled_jobs (job_id VARCHAR(36) PRIMARY KEY, expires_at VARCHAR(35) NOT NULL);
CREATE TABLE event_attempts (event_id VARCHAR(36) PRIMARY KEY, attempts INT NOT NULL DEFAULT 0, updated_at VARCHAR(35) NOT NULL);
