CREATE TABLE anomaly_events (
    id                BIGSERIAL        PRIMARY KEY,
    metric_name       VARCHAR(255)     NOT NULL,
    job_name          VARCHAR(128)     NOT NULL,
    event_timestamp   TIMESTAMPTZ      NOT NULL,
    value             DOUBLE PRECISION,
    threshold_upper   DOUBLE PRECISION,
    threshold_lower   DOUBLE PRECISION,
    midline           DOUBLE PRECISION,
    threshold_breached VARCHAR(16),
    severity          VARCHAR(16),
    algorithm_used    VARCHAR(64),
    deviation_score   DOUBLE PRECISION,
    operator_label    VARCHAR(32)      DEFAULT NULL,
    labeled_at        TIMESTAMPTZ
);

CREATE INDEX idx_anomaly_metric_ts ON anomaly_events (metric_name, event_timestamp DESC);
CREATE INDEX idx_anomaly_unlabeled ON anomaly_events (operator_label) WHERE operator_label IS NULL;
CREATE INDEX idx_anomaly_severity_ts ON anomaly_events (severity, event_timestamp DESC);
