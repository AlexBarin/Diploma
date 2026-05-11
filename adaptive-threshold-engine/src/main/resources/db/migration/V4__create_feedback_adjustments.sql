CREATE TABLE feedback_adjustments (
    id               BIGSERIAL        PRIMARY KEY,
    metric_name      VARCHAR(255)     NOT NULL,
    old_sensitivity  DOUBLE PRECISION NOT NULL,
    new_sensitivity  DOUBLE PRECISION NOT NULL,
    fdr_at_time      DOUBLE PRECISION NOT NULL,
    tp_count         INT              NOT NULL,
    fp_count         INT              NOT NULL,
    ex_count         INT              NOT NULL,
    direction        VARCHAR(16)      NOT NULL,
    adjusted_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_feedback_adj_metric ON feedback_adjustments (metric_name, adjusted_at DESC);
