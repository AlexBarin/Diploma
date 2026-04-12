package com.monitoring.threshold.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("threshold-engine")
public class ThresholdEngineProperties {

    private Prometheus prometheus = new Prometheus();
    private Defaults defaults = new Defaults();
    private Map<String, MetricOverride> metrics = new HashMap<>();

    public Prometheus getPrometheus() {
        return prometheus;
    }

    public void setPrometheus(Prometheus prometheus) {
        this.prometheus = prometheus;
    }

    public Defaults getDefaults() {
        return defaults;
    }

    public void setDefaults(Defaults defaults) {
        this.defaults = defaults;
    }

    public Map<String, MetricOverride> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, MetricOverride> metrics) {
        this.metrics = metrics;
    }

    public static class Prometheus {

        private String url = "http://prometheus:9090";
        private String queryStep = "5s";
        private String historyWindow = "1h";
        private Discovery discovery = new Discovery();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getQueryStep() {
            return queryStep;
        }

        public void setQueryStep(String queryStep) {
            this.queryStep = queryStep;
        }

        public String getHistoryWindow() {
            return historyWindow;
        }

        public void setHistoryWindow(String historyWindow) {
            this.historyWindow = historyWindow;
        }

        public Discovery getDiscovery() {
            return discovery;
        }

        public void setDiscovery(Discovery discovery) {
            this.discovery = discovery;
        }
    }

    public static class Discovery {

        private String mode = "auto"; // "auto" or "explicit"
        private List<String> excludeJobs = new ArrayList<>(List.of("predictive-module"));
        private List<String> excludeMetricPrefixes = new ArrayList<>(List.of("predictive_"));
        private Map<String, List<String>> includeMetrics = new HashMap<>();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public List<String> getExcludeJobs() {
            return excludeJobs;
        }

        public void setExcludeJobs(List<String> excludeJobs) {
            this.excludeJobs = excludeJobs;
        }

        public List<String> getExcludeMetricPrefixes() {
            return excludeMetricPrefixes;
        }

        public void setExcludeMetricPrefixes(List<String> excludeMetricPrefixes) {
            this.excludeMetricPrefixes = excludeMetricPrefixes;
        }

        public Map<String, List<String>> getIncludeMetrics() {
            return includeMetrics;
        }

        public void setIncludeMetrics(Map<String, List<String>> includeMetrics) {
            this.includeMetrics = includeMetrics;
        }

        public boolean isExplicitMode() {
            return "explicit".equalsIgnoreCase(mode) && !includeMetrics.isEmpty();
        }
    }

    public static class Defaults {

        private String algorithm = "adaptive-moving-stats";
        private double sensitivity = 3.0;
        private double emaAlpha = 0.1;
        private String windowSize = "1h";
        private String recomputeInterval = "30s";
        private int minDataPoints = 100;

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public double getSensitivity() {
            return sensitivity;
        }

        public void setSensitivity(double sensitivity) {
            this.sensitivity = sensitivity;
        }

        public double getEmaAlpha() {
            return emaAlpha;
        }

        public void setEmaAlpha(double emaAlpha) {
            this.emaAlpha = emaAlpha;
        }

        public String getWindowSize() {
            return windowSize;
        }

        public void setWindowSize(String windowSize) {
            this.windowSize = windowSize;
        }

        public String getRecomputeInterval() {
            return recomputeInterval;
        }

        public void setRecomputeInterval(String recomputeInterval) {
            this.recomputeInterval = recomputeInterval;
        }

        public int getMinDataPoints() {
            return minDataPoints;
        }

        public void setMinDataPoints(int minDataPoints) {
            this.minDataPoints = minDataPoints;
        }
    }

    public static class MetricOverride {

        private String algorithm;
        private Double sensitivity;
        private Integer seasonLength;

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public Double getSensitivity() {
            return sensitivity;
        }

        public void setSensitivity(Double sensitivity) {
            this.sensitivity = sensitivity;
        }

        public Integer getSeasonLength() {
            return seasonLength;
        }

        public void setSeasonLength(Integer seasonLength) {
            this.seasonLength = seasonLength;
        }
    }
}
