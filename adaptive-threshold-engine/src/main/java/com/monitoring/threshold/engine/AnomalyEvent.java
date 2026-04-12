package com.monitoring.threshold.engine;

import java.time.Instant;

public record AnomalyEvent(
        String metricName,
        Instant timestamp,
        double value,
        ThresholdBreached thresholdBreached,
        Severity severity,
        String algorithmUsed,
        double deviationScore
) {

    public enum ThresholdBreached {
        UPPER, LOWER
    }

    public enum Severity {
        WARNING, CRITICAL
    }
}
