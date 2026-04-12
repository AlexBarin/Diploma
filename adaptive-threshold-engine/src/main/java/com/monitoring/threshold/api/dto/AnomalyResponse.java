package com.monitoring.threshold.api.dto;

import java.time.Instant;

public record AnomalyResponse(
        String metricName,
        Instant timestamp,
        double value,
        String thresholdBreached,
        String severity,
        String algorithm,
        double deviationScore
) {
}
