CREATE TABLE IF NOT EXISTS algorithm_state_store (
    metric_key      VARCHAR(384)    PRIMARY KEY,
    algorithm_name  VARCHAR(64)     NOT NULL,
    state_json      TEXT            NOT NULL,
    saved_at        TIMESTAMPTZ     NOT NULL
);
