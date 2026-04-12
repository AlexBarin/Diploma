package com.monitoring.threshold.api.dto;

public record MetricAuditResponse(
        String metricName,
        String jobName,
        String assignedAlgorithm,
        long dataPointCount,
        boolean isReady,
        double currentUpper,
        double currentLower
) {
}
