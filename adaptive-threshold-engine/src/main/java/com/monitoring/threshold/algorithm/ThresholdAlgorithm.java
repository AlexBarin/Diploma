package com.monitoring.threshold.algorithm;

import java.util.List;

public interface ThresholdAlgorithm {

    String name();

    ThresholdResult compute(List<DataPoint> dataPoints, double sensitivity);

    void update(double value, long timestampEpochSeconds);

    ThresholdResult currentThresholds();

    boolean isReady();

    default String serializeState() {
        return "{}";
    }

    default boolean supportsSerialize() {
        return false;
    }

    record DataPoint(long timestamp, double value) {
    }

    record ThresholdResult(double upper, double lower, double midline, String algorithmName) {
    }
}
