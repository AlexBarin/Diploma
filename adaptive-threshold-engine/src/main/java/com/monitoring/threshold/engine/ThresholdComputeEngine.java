package com.monitoring.threshold.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.monitoring.threshold.algorithm.AlgorithmSelector;
import com.monitoring.threshold.algorithm.HoltWintersSmoothing;
import com.monitoring.threshold.algorithm.OutlierFilter;
import com.monitoring.threshold.algorithm.OutlierFilter.DriftResult;
import com.monitoring.threshold.algorithm.ThresholdAlgorithm;
import com.monitoring.threshold.algorithm.ThresholdAlgorithm.DataPoint;
import com.monitoring.threshold.algorithm.ThresholdAlgorithm.ThresholdResult;
import com.monitoring.threshold.config.ThresholdEngineProperties;
import com.monitoring.threshold.export.GrafanaAnnotationService;
import com.monitoring.threshold.persistence.AlgorithmStateService;
import com.monitoring.threshold.notification.EmailAlertService;
import com.monitoring.threshold.persistence.AnomalyRepository;
import com.monitoring.threshold.prometheus.PrometheusMetricDiscovery;
import com.monitoring.threshold.prometheus.PrometheusQueryService;
import com.monitoring.threshold.prometheus.dto.PrometheusResponse.PrometheusResult;

import jakarta.annotation.PostConstruct;

@Service
public class ThresholdComputeEngine {

    private static final Logger log = LoggerFactory.getLogger(ThresholdComputeEngine.class);

    private final PrometheusQueryService queryService;
    private final PrometheusMetricDiscovery metricDiscovery;
    private final AlgorithmSelector algorithmSelector;
    private final ThresholdEngineProperties properties;
    private final GrafanaAnnotationService grafanaAnnotationService;
    private final AlgorithmStateService algorithmStateService;
    private final AnomalyRepository anomalyRepository;
    private final EmailAlertService emailAlertService;

    private final ConcurrentHashMap<String, MetricState> metricStates = new ConcurrentHashMap<>();
    private final AtomicLong lastComputeTimestamp = new AtomicLong(0);
    private volatile long lastComputeDurationMs = 0;

    public ThresholdComputeEngine(PrometheusQueryService queryService,
                                   PrometheusMetricDiscovery metricDiscovery,
                                   AlgorithmSelector algorithmSelector,
                                   ThresholdEngineProperties properties,
                                   GrafanaAnnotationService grafanaAnnotationService,
                                   AlgorithmStateService algorithmStateService,
                                   AnomalyRepository anomalyRepository,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false)
                                   EmailAlertService emailAlertService) {
        this.queryService = queryService;
        this.metricDiscovery = metricDiscovery;
        this.algorithmSelector = algorithmSelector;
        this.properties = properties;
        this.grafanaAnnotationService = grafanaAnnotationService;
        this.algorithmStateService = algorithmStateService;
        this.anomalyRepository = anomalyRepository;
        this.emailAlertService = emailAlertService;

        if (emailAlertService != null) {
            log.info("Email alerting is ENABLED");
        } else {
            log.info("Email alerting is DISABLED (set ALERT_EMAIL_ENABLED=true to enable)");
        }
    }

    @PostConstruct
    public void init() {
        log.info("Initializing ThresholdComputeEngine, discovering metrics...");
        discoverAndRegisterMetrics();
    }

    @Scheduled(fixedDelayString = "${threshold-engine.defaults.recompute-interval-ms:30000}")
    public void computeThresholds() {
        long startTime = System.currentTimeMillis();
        log.debug("Starting threshold computation cycle for {} metrics", metricStates.size());

        Instant now = Instant.now();
        Duration historyWindow = parseHistoryWindow(properties.getPrometheus().getHistoryWindow());
        Instant start = now.minus(historyWindow);
        String step = properties.getPrometheus().getQueryStep();

        for (Map.Entry<String, MetricState> entry : metricStates.entrySet()) {
            String metricKey = entry.getKey();
            MetricState state = entry.getValue();

            try {
                processMetric(metricKey, state, start, now, step);
            } catch (Exception e) {
                log.error("Error processing metric '{}': {}", metricKey, e.getMessage());
            }
        }

        lastComputeTimestamp.set(now.getEpochSecond());
        lastComputeDurationMs = System.currentTimeMillis() - startTime;
        log.debug("Threshold computation cycle completed in {}ms", lastComputeDurationMs);
    }

    @Scheduled(fixedDelay = 300000)
    public void periodicRediscovery() {
        log.info("Running periodic metric re-discovery...");
        discoverAndRegisterMetrics();
    }

    private void discoverAndRegisterMetrics() {
        try {
            Map<String, List<String>> discovered = metricDiscovery.discoverMetrics();
            int newCount = 0;

            for (Map.Entry<String, List<String>> jobEntry : discovered.entrySet()) {
                String jobName = jobEntry.getKey();
                for (String metricName : jobEntry.getValue()) {
                    String key = buildMetricKey(jobName, metricName);
                    if (!metricStates.containsKey(key)) {
                        MetricState state = new MetricState(metricName, jobName, Map.of("job", jobName));
                        metricStates.put(key, state);
                        newCount++;
                    }
                }
            }

            if (newCount > 0) {
                log.info("Registered {} new metrics, total tracked: {}", newCount, metricStates.size());
            }
        } catch (Exception e) {
            log.error("Failed to discover metrics: {}", e.getMessage());
        }
    }

    private void processMetric(String metricKey, MetricState state,
                                Instant start, Instant end, String step) {
        String selector = state.getMetricName() + "{job=\"" + state.getJobName() + "\"}";
        String query;
        if (state.getMetricName().endsWith("_total") || state.getMetricName().endsWith("_created")) {
            query = "sum(rate(" + selector + "[1m]))";
        } else {
            query = "sum(" + selector + ")";
        }

        List<PrometheusResult> results = queryService.queryRange(query, start, end, step);
        if (results.isEmpty()) {
            return;
        }

        PrometheusResult result = results.getFirst();
        List<DataPoint> dataPoints = new ArrayList<>();
        for (List<Object> valuePair : result.values()) {
            if (valuePair.size() >= 2) {
                long timestamp = parseTimestamp(valuePair.get(0));
                double value = parseDouble(valuePair.get(1));
                if (!Double.isNaN(value)) {
                    dataPoints.add(new DataPoint(timestamp, value));
                }
            }
        }

        if (dataPoints.isEmpty()) {
            return;
        }

        List<DataPoint> cleanData = OutlierFilter.removeExtremeOutliers(dataPoints);

        if (state.getAlgorithm() == null) {
            var restored = algorithmStateService.restore(metricKey);
            if (restored.isPresent()) {
                ThresholdAlgorithm restoredAlgo = restored.get();

                if (restoredAlgo instanceof HoltWintersSmoothing hw && !cleanData.isEmpty()) {
                    double[] sortedVals = cleanData.stream().mapToDouble(DataPoint::value).sorted().toArray();
                    double currentMedian = sortedVals[sortedVals.length / 2];
                    double hwLevel = hw.getLevel();
                    double divergence = Math.abs(hwLevel - currentMedian) / Math.max(Math.abs(currentMedian), 1.0);
                    if (divergence > 5.0) {
                        log.warn("Discarding stale Holt-Winters state for '{}': level={} but current median={} ({}x off)",
                                metricKey, hwLevel, currentMedian, String.format("%.1f", divergence));
                        restoredAlgo = null;
                    }
                }

                if (restoredAlgo != null) {
                    state.setAlgorithm(restoredAlgo);
                    log.info("Restored algorithm '{}' from saved state for metric '{}'",
                            restoredAlgo.name(), metricKey);
                }
            }
            if (state.getAlgorithm() == null) {
                double[] values = cleanData.stream().mapToDouble(DataPoint::value).toArray();
                ThresholdAlgorithm algorithm = algorithmSelector.selectAlgorithm(
                        state.getMetricName(), values, properties);
                state.setAlgorithm(algorithm);
                log.info("Assigned algorithm '{}' to metric '{}'", algorithm.name(), metricKey);
            }
        }

        ThresholdAlgorithm algorithm = state.getAlgorithm();
        long lastTs = state.getLastProcessedTimestamp();
        for (DataPoint dp : cleanData) {
            if (dp.timestamp() > lastTs) {
                algorithm.update(dp.value(), dp.timestamp());
            }
        }
        if (!cleanData.isEmpty()) {
            state.setLastProcessedTimestamp(cleanData.getLast().timestamp());
        }

        double sensitivity = properties.getDefaults().getSensitivity();
        ThresholdEngineProperties.MetricOverride override =
                properties.getMetrics().get(state.getMetricName());
        if (override != null && override.getSensitivity() != null) {
            sensitivity = override.getSensitivity();
        }

        ThresholdResult rawResult = algorithm.compute(cleanData, sensitivity);

        boolean skipDriftGuard = "rate-of-change".equals(rawResult.algorithmName());

        if (!skipDriftGuard){
            DriftResult drift = OutlierFilter.detectDrift(dataPoints);
            if (drift.isUpwardDrift()) {
                double baselineUpper = drift.baselineMedian()
                    + sensitivity * (rawResult.upper() - rawResult.midline());
                double adjustedUpper = Math.min(rawResult.upper(), baselineUpper);
                rawResult = new ThresholdResult(adjustedUpper, rawResult.lower(),
                    rawResult.midline(), rawResult.algorithmName() + "+drift-guard");
                log.debug("Drift guard active for '{}': baseline={}, recent={}, magnitude={}σ",
                    metricKey, drift.baselineMedian(), drift.recentMedian(),
                    String.format("%.1f", drift.driftMagnitude()));
            }
        }

        double bandWidth = rawResult.upper() - rawResult.lower();
        double minWidth = Math.max(1.0, 0.01 * Math.abs(rawResult.midline()));
        if (bandWidth < minWidth) {
            double half = minWidth / 2.0;
            rawResult = new ThresholdResult(
                    rawResult.midline() + half,
                    rawResult.midline() - half,
                    rawResult.midline(),
                    rawResult.algorithmName()
            );
        }

        ThresholdResult thresholdResult = new ThresholdResult(
                rawResult.upper(),
                Math.max(0, rawResult.lower()),
                rawResult.midline(),
                rawResult.algorithmName()
        );
        state.setCurrentThresholdResult(thresholdResult);
        state.incrementDataPointCount(dataPoints.size());
        state.setLastUpdated(Instant.now());

        DataPoint latest = dataPoints.getLast();
        checkForAnomaly(state, latest, thresholdResult);
    }

    private void checkForAnomaly(MetricState state, DataPoint latest, ThresholdResult thresholds) {
        state.setCurrentAnomalyScore(0.0);

        if (!state.getAlgorithm().isReady()) {
            return;
        }

        double value = latest.value();
        double midline = thresholds.midline();

        if (value > thresholds.upper()) {
            double deviation = (midline != 0) ? Math.abs(value - midline) / Math.abs(thresholds.upper() - midline) : 0;
            deviation = Math.min(deviation, 10.0);
            state.setCurrentAnomalyScore(deviation);
            AnomalyEvent.Severity severity = deviation > 2.0
                    ? AnomalyEvent.Severity.CRITICAL
                    : AnomalyEvent.Severity.WARNING;

            AnomalyEvent event = new AnomalyEvent(
                    state.getMetricName(),
                    Instant.ofEpochSecond(latest.timestamp()),
                    value,
                    AnomalyEvent.ThresholdBreached.UPPER,
                    severity,
                    state.getAlgorithm().name(),
                    deviation
            );

            state.addAnomaly(event);
            grafanaAnnotationService.pushAnomaly(event);
            persistAnomaly(event, state);
            sendEmailAlert(event);

        } else if (value < thresholds.lower()) {
            double deviation = (midline != 0) ? Math.abs(value - midline) / Math.abs(midline - thresholds.lower()) : 0;
            deviation = Math.min(deviation, 10.0);
            state.setCurrentAnomalyScore(deviation);
            AnomalyEvent.Severity severity = deviation > 2.0
                    ? AnomalyEvent.Severity.CRITICAL
                    : AnomalyEvent.Severity.WARNING;

            AnomalyEvent event = new AnomalyEvent(
                    state.getMetricName(),
                    Instant.ofEpochSecond(latest.timestamp()),
                    value,
                    AnomalyEvent.ThresholdBreached.LOWER,
                    severity,
                    state.getAlgorithm().name(),
                    deviation
            );

            state.addAnomaly(event);
            grafanaAnnotationService.pushAnomaly(event);
            persistAnomaly(event, state);
            sendEmailAlert(event);
        }
    }

    private void persistAnomaly(AnomalyEvent event, MetricState state) {
        try {
            anomalyRepository.saveAnomaly(event, state);
        } catch (Exception e) {
            log.warn("Failed to persist anomaly for '{}': {}", event.metricName(), e.getMessage());
        }
    }

    private void sendEmailAlert(AnomalyEvent event) {
        if (emailAlertService != null) {
            try {
                emailAlertService.trySendAlert(event);
            } catch (Exception e) {
                log.warn("Failed to send email alert for '{}': {}", event.metricName(), e.getMessage());
            }
        }
    }

    public Map<String, MetricState> getMetricStates() {
        return Collections.unmodifiableMap(metricStates);
    }

    public MetricState getMetricState(String metricName) {
        return metricStates.values().stream()
                .filter(s -> s.getMetricName().equals(metricName))
                .findFirst()
                .orElse(null);
    }

    public List<AnomalyEvent> getRecentAnomalies(Duration since) {
        Instant cutoff = Instant.now().minus(since);
        List<AnomalyEvent> anomalies = new ArrayList<>();
        for (MetricState state : metricStates.values()) {
            for (AnomalyEvent event : state.getRecentAnomalies()) {
                if (event.timestamp().isAfter(cutoff)) {
                    anomalies.add(event);
                }
            }
        }
        anomalies.sort((a, b) -> b.timestamp().compareTo(a.timestamp()));
        return anomalies;
    }

    public long getLastComputeTimestamp() {
        return lastComputeTimestamp.get();
    }

    public long getLastComputeDurationMs() {
        return lastComputeDurationMs;
    }

    public int getTrackedMetricCount() {
        return metricStates.size();
    }

    public void updateMetricConfig(String metricName, String algorithm, Double sensitivity) {
        ThresholdEngineProperties.MetricOverride override =
                properties.getMetrics().computeIfAbsent(metricName, k -> new ThresholdEngineProperties.MetricOverride());
        if (algorithm != null) {
            override.setAlgorithm(algorithm);
        }
        if (sensitivity != null) {
            override.setSensitivity(sensitivity);
        }

        metricStates.values().stream()
                .filter(s -> s.getMetricName().equals(metricName))
                .forEach(s -> s.setAlgorithm(null));

        log.info("Updated config for metric '{}': algorithm={}, sensitivity={}", metricName, algorithm, sensitivity);
    }

    private static String buildMetricKey(String jobName, String metricName) {
        return jobName + "/" + metricName;
    }

    private static Duration parseHistoryWindow(String window) {
        if (window == null || window.isEmpty()) return Duration.ofHours(1);
        char unit = window.charAt(window.length() - 1);
        long value = Long.parseLong(window.substring(0, window.length() - 1));
        return switch (unit) {
            case 's' -> Duration.ofSeconds(value);
            case 'm' -> Duration.ofMinutes(value);
            case 'h' -> Duration.ofHours(value);
            case 'd' -> Duration.ofDays(value);
            default -> Duration.ofHours(1);
        };
    }

    private static long parseTimestamp(Object raw) {
        if (raw instanceof Number num) {
            return num.longValue();
        }
        return Long.parseLong(raw.toString().split("\\.")[0]);
    }

    private static double parseDouble(Object raw) {
        if (raw instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return Double.parseDouble(raw.toString());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
