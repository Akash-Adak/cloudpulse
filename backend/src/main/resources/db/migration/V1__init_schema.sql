-- CloudPulse initial schema (MySQL 8)

CREATE TABLE monitored_service (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    name                   VARCHAR(100)  NOT NULL,
    url                    VARCHAR(2048) NOT NULL,
    provider               VARCHAR(20)   NOT NULL,
    check_interval_seconds INT           NOT NULL,
    timeout_ms             INT           NOT NULL,
    failure_threshold      INT           NOT NULL,
    enabled                BOOLEAN       NOT NULL DEFAULT TRUE,
    status                 VARCHAR(10)   NOT NULL DEFAULT 'UNKNOWN',
    consecutive_failures   INT           NOT NULL DEFAULT 0,
    consecutive_successes  INT           NOT NULL DEFAULT 0,
    last_checked_at        DATETIME(6)   NULL,
    last_status_code       INT           NULL,
    last_response_time_ms  INT           NULL,
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE health_check (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    service_id       BIGINT       NOT NULL,
    checked_at       DATETIME(6)  NOT NULL,
    success          BOOLEAN      NOT NULL,
    status_code      INT          NULL,
    response_time_ms INT          NULL,
    error_message    VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_check_service_time (service_id, checked_at),
    KEY idx_check_time (checked_at),
    CONSTRAINT fk_check_service FOREIGN KEY (service_id)
        REFERENCES monitored_service (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE incident (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    service_id  BIGINT       NOT NULL,
    started_at  DATETIME(6)  NOT NULL,
    resolved_at DATETIME(6)  NULL,
    reason      VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_incident_service (service_id, started_at),
    KEY idx_incident_open (resolved_at),
    CONSTRAINT fk_incident_service FOREIGN KEY (service_id)
        REFERENCES monitored_service (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
