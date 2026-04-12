package com.monitoring.threshold.algorithm;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AdaptiveMovingStats implements ThresholdAlgorithm {

    private final double alpha;
    private final double sensitivity;
    private final int minDataPoints;

    private double mu = 0.0;
    private double sigma = 0.0;
    private long count = 0;
    private boolean initialized = false;

    public AdaptiveMovingStats(double alpha, double sensitivity, int minDataPoints) {
        this.alpha = alpha;
        this.sensitivity = sensitivity;
        this.minDataPoints = minDataPoints;
    }

    public AdaptiveMovingStats() {
        this(0.1, 3.0, 100);
    }

    @Override
    public String name() {
        return "adaptive-moving-stats";
    }

    @Override
    public ThresholdResult compute(List<DataPoint> dataPoints, double sensitivity) {
        double localMu = 0.0;
        double localSigma = 0.0;
        boolean localInitialized = false;

        for (DataPoint dp : dataPoints) {
            if (!localInitialized) {
                localMu = dp.value();
                localSigma = 0.0;
                localInitialized = true;
            } else {
                localMu = alpha * dp.value() + (1 - alpha) * localMu;
                localSigma = alpha * Math.abs(dp.value() - localMu) + (1 - alpha) * localSigma;
            }
        }

        double upper = localMu + sensitivity * localSigma;
        double lower = localMu - sensitivity * localSigma;
        return new ThresholdResult(upper, lower, localMu, name());
    }

    @Override
    public void update(double value, long timestampEpochSeconds) {
        if (!initialized) {
            mu = value;
            sigma = 0.0;
            initialized = true;
        } else {
            mu = alpha * value + (1 - alpha) * mu;
            sigma = alpha * Math.abs(value - mu) + (1 - alpha) * sigma;
        }
        count++;
    }

    @Override
    public ThresholdResult currentThresholds() {
        double upper = mu + sensitivity * sigma;
        double lower = mu - sensitivity * sigma;
        return new ThresholdResult(upper, lower, mu, name());
    }

    @Override
    public boolean isReady() {
        return count >= minDataPoints;
    }

    public long getCount() {
        return count;
    }

    @Override
    public boolean supportsSerialize() {
        return true;
    }

    @Override
    public String serializeState() {
        try {
            return new ObjectMapper().writeValueAsString(Map.of(
                    "alpha", alpha, "sensitivity", sensitivity, "minDataPoints", minDataPoints,
                    "mu", mu, "sigma", sigma, "count", count, "initialized", initialized));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    public static AdaptiveMovingStats restoreFrom(String json) {
        try {
            Map<String, Object> m = new ObjectMapper().readValue(json, Map.class);
            double alpha = ((Number) m.get("alpha")).doubleValue();
            double sensitivity = ((Number) m.get("sensitivity")).doubleValue();
            int minDataPoints = ((Number) m.get("minDataPoints")).intValue();
            AdaptiveMovingStats instance = new AdaptiveMovingStats(alpha, sensitivity, minDataPoints);
            instance.mu = ((Number) m.get("mu")).doubleValue();
            instance.sigma = ((Number) m.get("sigma")).doubleValue();
            instance.count = ((Number) m.get("count")).longValue();
            instance.initialized = (Boolean) m.get("initialized");
            return instance;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to restore AdaptiveMovingStats", e);
        }
    }
}
