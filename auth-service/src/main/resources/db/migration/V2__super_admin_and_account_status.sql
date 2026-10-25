ALTER TABLE users ADD status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
UPDATE users SET role = 'SUPER_ADMIN' WHERE username = 'admin';

CREATE TABLE audit_logs (
    log_id VARCHAR(36) PRIMARY KEY,
    action VARCHAR(80) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    details VARCHAR(1000),
    created_at VARCHAR(35) NOT NULL
);
