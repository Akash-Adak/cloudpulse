ALTER TABLE monitored_service
    ADD COLUMN actuator_metrics_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE metric_sample (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    service_id  BIGINT        NOT NULL,
    metric_name VARCHAR(150)  NOT NULL,
    value       DOUBLE        NOT NULL,
    sampled_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_metric_service_name_time (service_id, metric_name, sampled_at),
    CONSTRAINT fk_metric_service FOREIGN KEY (service_id)
        REFERENCES monitored_service (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
