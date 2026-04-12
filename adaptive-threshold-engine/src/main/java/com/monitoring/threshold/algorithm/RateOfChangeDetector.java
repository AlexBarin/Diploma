package com.monitoring.threshold.algorithm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RateOfChangeDetector implements ThresholdAlgorithm {

    private final double alpha;
    private final double sensitivity;
    private final int minDataPoints;

    private double previousValue = Double.NaN;
    private long previousTimestamp = -1;
    private double currentValue = Double.NaN;

    private double muDx = 0.0;
    private double sigmaDx = 0.0;
    private double lastDelta = 1.0;
    private boolean derivativeInitialized = false;
    private long count = 0;

    public RateOfChangeDetector(double alpha, double sensitivity, int minDataPoints) {
        this.alpha = alpha;
        this.sensitivity = sensitivity;
        this.minDataPoints = minDataPoints;
    }

    public RateOfChangeDetector() {
        this(0.1, 3.0, 100);
    }

    @Override
    public String name() {
        return "rate-of-change";
    }

    @Override
    public ThresholdResult compute(List<DataPoint> dataPoints, double sensitivity) {
        if (dataPoints.size() < 2) {
            return new ThresholdResult(Double.MAX_VALUE, -Double.MAX_VALUE, 0.0, name());
        }

        double localMuDx = 0.0;
        double localSigmaDx = 0.0;
        boolean localInit = false;
        double localCurrent = dataPoints.getLast().value();
        double localDelta = 1.0;

        for (int i = 1; i < dataPoints.size(); i++) {
            DataPoint prev = dataPoints.get(i - 1);
            DataPoint curr = dataPoints.get(i);
            double dt = curr.timestamp() - prev.timestamp();
            if (dt <= 0) continue;

            double dx = (curr.value() - prev.value()) / dt;
            localDelta = dt;

            if (!localInit) {
                localMuDx = dx;
                localSigmaDx = 0.0;
                localInit = true;
            } else {
                localMuDx = alpha * dx + (1 - alpha) * localMuDx;
                localSigmaDx = alpha * Math.abs(dx - localMuDx) + (1 - alpha) * localSigmaDx;
            }
        }

        double spread = sensitivity * localSigmaDx * localDelta;
        return new ThresholdResult(localCurrent + spread, localCurrent - spread, localCurrent, name());
    }

    @Override
    public void update(double value, long timestampEpochSeconds) {
        count++;
        currentValue = value;

        if (Double.isNaN(previousValue)) {
            previousValue = value;
            previousTimestamp = timestampEpochSeconds;
            return;
        }

        double dt = timestampEpochSeconds - previousTimestamp;
        if (dt <= 0) {
            previousValue = value;
            previousTimestamp = timestampEpochSeconds;
            return;
        }

        double dx = (value - previousValue) / dt;
        lastDelta = dt;

        if (!derivativeInitialized) {
            muDx = dx;
            sigmaDx = 0.0;
            derivativeInitialized = true;
        } else {
            muDx = alpha * dx + (1 - alpha) * muDx;
            sigmaDx = alpha * Math.abs(dx - muDx) + (1 - alpha) * sigmaDx;
        }

        previousValue = value;
        previousTimestamp = timestampEpochSeconds;
    }

    @Override
    public ThresholdResult currentThresholds() {
        if (Double.isNaN(currentValue) || !derivativeInitialized) {
            return new ThresholdResult(Double.MAX_VALUE, -Double.MAX_VALUE, 0.0, name());
        }

        double spread = sensitivity * sigmaDx * lastDelta;
        return new ThresholdResult(currentValue + spread, currentValue - spread, currentValue, name());
    }

    @Override
    public boolean isReady() {
        return count >= minDataPoints && derivativeInitialized;
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
            Map<String, Object> m = new HashMap<>();
            m.put("alpha", alpha);
            m.put("sensitivity", sensitivity);
            m.put("minDataPoints", minDataPoints);
            m.put("previousValue", previousValue);
            m.put("previousTimestamp", previousTimestamp);
            m.put("currentValue", currentValue);
            m.put("muDx", muDx);
            m.put("sigmaDx", sigmaDx);
            m.put("lastDelta", lastDelta);
            m.put("derivativeInitialized", derivativeInitialized);
            m.put("count", count);
            return new ObjectMapper().writeValueAsString(m);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    public static RateOfChangeDetector restoreFrom(String json) {
        try {
            Map<String, Object> m = new ObjectMapper().readValue(json, Map.class);
            double alpha = ((Number) m.get("alpha")).doubleValue();
            double sensitivity = ((Number) m.get("sensitivity")).doubleValue();
            int minDataPoints = ((Number) m.get("minDataPoints")).intValue();
            RateOfChangeDetector instance = new RateOfChangeDetector(alpha, sensitivity, minDataPoints);
            instance.previousValue = ((Number) m.get("previousValue")).doubleValue();
            instance.previousTimestamp = ((Number) m.get("previousTimestamp")).longValue();
            instance.currentValue = ((Number) m.get("currentValue")).doubleValue();
            instance.muDx = ((Number) m.get("muDx")).doubleValue();
            instance.sigmaDx = ((Number) m.get("sigmaDx")).doubleValue();
            instance.lastDelta = ((Number) m.get("lastDelta")).doubleValue();
            instance.derivativeInitialized = (Boolean) m.get("derivativeInitialized");
            instance.count = ((Number) m.get("count")).longValue();
            return instance;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to restore RateOfChangeDetector", e);
        }
    }
}
