-- Apply once before enabling the operation audit log.
CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    method VARCHAR(10) NOT NULL,
    path VARCHAR(255) NOT NULL,
    status_code INT NOT NULL,
    client_ip VARCHAR(64) NULL,
    created_at DATETIME NOT NULL
);

CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);
CREATE INDEX idx_audit_log_username ON audit_log (username);
