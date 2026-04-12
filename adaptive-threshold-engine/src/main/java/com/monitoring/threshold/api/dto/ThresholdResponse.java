package com.monitoring.threshold.api.dto;

import java.time.Instant;

public record ThresholdResponse(
        String metricName,
        String jobName,
        String algorithm,
        double upper,
        double lower,
        double midline,
        Instant lastUpdated,
        long dataPointCount
) {
}
