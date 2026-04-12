package com.monitoring.threshold.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.monitoring.threshold.algorithm.ThresholdAlgorithm;
import com.monitoring.threshold.algorithm.ThresholdAlgorithm.ThresholdResult;

public class MetricState {

    private final String metricName;
    private final String jobName;
    private final Map<String, String> labels;

    private ThresholdAlgorithm algorithm;
    private ThresholdResult currentThresholdResult;
    private final List<AnomalyEvent> recentAnomalies = Collections.synchronizedList(new ArrayList<>());
    private Instant lastUpdated;
    private long dataPointCount;
    private volatile double currentAnomalyScore = 0.0;
    private long lastProcessedTimestamp = 0;

    public MetricState(String metricName, String jobName, Map<String, String> labels) {
        this.metricName = metricName;
        this.jobName = jobName;
        this.labels = labels != null ? new HashMap<>(labels) : new HashMap<>();
        this.lastUpdated = Instant.now();
        this.dataPointCount = 0;
    }

    public String getMetricName() {
        return metricName;
    }

    public String getJobName() {
        return jobName;
    }

    public Map<String, String> getLabels() {
        return Collections.unmodifiableMap(labels);
    }

    public ThresholdAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(ThresholdAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public ThresholdResult getCurrentThresholdResult() {
        return currentThresholdResult;
    }

    public void setCurrentThresholdResult(ThresholdResult currentThresholdResult) {
        this.currentThresholdResult = currentThresholdResult;
    }

    public List<AnomalyEvent> getRecentAnomalies() {
        return Collections.unmodifiableList(new ArrayList<>(recentAnomalies));
    }

    public void addAnomaly(AnomalyEvent event) {
        recentAnomalies.add(event);
        while (recentAnomalies.size() > 1000) {
            recentAnomalies.removeFirst();
        }
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public long getDataPointCount() {
        return dataPointCount;
    }

    public void setDataPointCount(long dataPointCount) {
        this.dataPointCount = dataPointCount;
    }

    public void incrementDataPointCount(long delta) {
        this.dataPointCount += delta;
    }

    public double getCurrentAnomalyScore() {
        return currentAnomalyScore;
    }

    public void setCurrentAnomalyScore(double currentAnomalyScore) {
        this.currentAnomalyScore = currentAnomalyScore;
    }

    public long getLastProcessedTimestamp() {
        return lastProcessedTimestamp;
    }

    public void setLastProcessedTimestamp(long lastProcessedTimestamp) {
        this.lastProcessedTimestamp = lastProcessedTimestamp;
    }
}
