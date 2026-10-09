CREATE TABLE narration_jobs (
    job_id             VARCHAR(64)   NOT NULL PRIMARY KEY,
    content_id         VARCHAR(128)  NOT NULL,
    content_version    INT           NOT NULL,
    status             VARCHAR(32)   NOT NULL,
    created_by         VARCHAR(128)  NOT NULL,
    correlation_id     VARCHAR(128)  NOT NULL,
    retry_of_job_id    VARCHAR(64)   NULL,
    created_at         DATETIME2(3)  NOT NULL,
    updated_at         DATETIME2(3)  NOT NULL,
    finished_at        DATETIME2(3)  NULL,
    CONSTRAINT CK_narration_jobs_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','PARTIALLY_COMPLETED','FAILED','CANCELLED'))
);
CREATE INDEX IX_narration_jobs_content_version_status ON narration_jobs(content_id, content_version, status);
CREATE INDEX IX_narration_jobs_status_created ON narration_jobs(status, created_at);

CREATE TABLE narration_job_targets (
    target_id          VARCHAR(64)   NOT NULL PRIMARY KEY,
    job_id             VARCHAR(64)   NOT NULL,
    lang               VARCHAR(32)   NOT NULL,
    voice_id           VARCHAR(128)  NOT NULL,
    status             VARCHAR(32)   NOT NULL,
    error_code         VARCHAR(64)   NULL,
    translation_id     VARCHAR(128)  NULL,
    audio_id           VARCHAR(128)  NULL,
    created_at         DATETIME2(3)  NOT NULL,
    updated_at         DATETIME2(3)  NOT NULL,
    CONSTRAINT FK_narration_targets_job FOREIGN KEY (job_id) REFERENCES narration_jobs(job_id),
    CONSTRAINT UQ_narration_targets_job_lang UNIQUE (job_id, lang),
    CONSTRAINT CK_narration_targets_status CHECK (status IN ('PENDING','TRANSLATING','SYNTHESIZING','PUBLISHED','FAILED','CANCELLED'))
);
CREATE INDEX IX_narration_targets_job_status ON narration_job_targets(job_id, status);
CREATE INDEX IX_narration_targets_status_updated ON narration_job_targets(status, updated_at);
CREATE INDEX IX_narration_targets_lang_status ON narration_job_targets(lang, status);

CREATE TABLE idempotency_records (
    id                  UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    user_id             VARCHAR(128)     NOT NULL,
    operation           VARCHAR(64)      NOT NULL,
    idempotency_key     VARCHAR(255)     NOT NULL,
    request_hash        CHAR(64)         NOT NULL,
    job_id              VARCHAR(64)      NOT NULL,
    expires_at          DATETIME2(3)     NOT NULL,
    created_at          DATETIME2(3)     NOT NULL,
    CONSTRAINT UQ_idempotency_scope UNIQUE (user_id, operation, idempotency_key),
    CONSTRAINT FK_idempotency_job FOREIGN KEY (job_id) REFERENCES narration_jobs(job_id)
);
CREATE INDEX IX_idempotency_expiry ON idempotency_records(expires_at);

CREATE TABLE outbox_events (
    id                  UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    event_id            UNIQUEIDENTIFIER NOT NULL,
    exchange_name       VARCHAR(128)     NOT NULL,
    routing_key         VARCHAR(128)     NOT NULL,
    event_type          VARCHAR(128)     NOT NULL,
    correlation_id      VARCHAR(128)     NOT NULL,
    payload_json        NVARCHAR(MAX)    NOT NULL,
    occurred_at         DATETIME2(3)     NOT NULL,
    created_at          DATETIME2(3)     NOT NULL,
    sent_at             DATETIME2(3)     NULL,
    attempts            INT              NOT NULL DEFAULT 0,
    last_error          NVARCHAR(2000)   NULL,
    CONSTRAINT UQ_outbox_event_id UNIQUE (event_id)
);
CREATE INDEX IX_outbox_pending ON outbox_events(sent_at, created_at);

CREATE TABLE cancelled_jobs (
    job_id              VARCHAR(64)  NOT NULL PRIMARY KEY,
    cancelled_at        DATETIME2(3) NOT NULL,
    expires_at          DATETIME2(3) NOT NULL
);
CREATE INDEX IX_cancelled_jobs_expiry ON cancelled_jobs(expires_at);
