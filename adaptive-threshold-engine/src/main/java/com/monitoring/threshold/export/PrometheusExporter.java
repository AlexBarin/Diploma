package com.monitoring.threshold.export;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.monitoring.threshold.algorithm.ThresholdAlgorithm.ThresholdResult;
import com.monitoring.threshold.engine.AnomalyEvent;
import com.monitoring.threshold.engine.MetricState;
import com.monitoring.threshold.engine.ThresholdComputeEngine;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

@Component
public class PrometheusExporter {

    private static final Logger log = LoggerFactory.getLogger(PrometheusExporter.class);

    private final MeterRegistry meterRegistry;
    private final ThresholdComputeEngine engine;

    private final ConcurrentHashMap<String, Boolean> registeredMetrics = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Counter> anomalyCounters = new ConcurrentHashMap<>();

    private final DistributionSummary computeDuration;

    public PrometheusExporter(MeterRegistry meterRegistry, ThresholdComputeEngine engine) {
        this.meterRegistry = meterRegistry;
        this.engine = engine;

        Gauge.builder("adaptive_engine_metrics_tracked_total", engine, ThresholdComputeEngine::getTrackedMetricCount)
                .description("Total number of metrics being tracked")
                .register(meterRegistry);

        Gauge.builder("adaptive_engine_last_compute_timestamp_seconds", engine,
                        ThresholdComputeEngine::getLastComputeTimestamp)
                .description("Timestamp of the last compute cycle")
                .register(meterRegistry);

        this.computeDuration = DistributionSummary.builder("adaptive_engine_compute_duration_seconds")
                .description("Duration of threshold computation cycles")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 10000)
    public void exportMetrics() {
        Map<String, MetricState> states = engine.getMetricStates();

        for (Map.Entry<String, MetricState> entry : states.entrySet()) {
            MetricState state = entry.getValue();
            ThresholdResult result = state.getCurrentThresholdResult();
            if (result == null) continue;

            String metricName = state.getMetricName();
            String jobName = state.getJobName();
            String algorithmName = result.algorithmName();
            String key = metricName + ":" + jobName + ":" + algorithmName;

            if (registeredMetrics.putIfAbsent(key, Boolean.TRUE) == null) {
                Tags tags = Tags.of("metric", metricName, "job", jobName, "algorithm", algorithmName);

                Gauge.builder("adaptive_threshold_upper", state,
                                s -> s.getCurrentThresholdResult() != null ? s.getCurrentThresholdResult().upper() : 0)
                        .tags(tags)
                        .description("Adaptive upper threshold")
                        .register(meterRegistry);

                Gauge.builder("adaptive_threshold_lower", state,
                                s -> s.getCurrentThresholdResult() != null ? s.getCurrentThresholdResult().lower() : 0)
                        .tags(tags)
                        .description("Adaptive lower threshold")
                        .register(meterRegistry);

                Gauge.builder("adaptive_threshold_midline", state,
                                s -> s.getCurrentThresholdResult() != null ? s.getCurrentThresholdResult().midline() : 0)
                        .tags(tags)
                        .description("Adaptive midline value")
                        .register(meterRegistry);

                Tags anomalyTags = Tags.of("metric", metricName);
                Gauge.builder("adaptive_anomaly_score", state, MetricState::getCurrentAnomalyScore)
                        .tags(anomalyTags)
                        .description("Latest anomaly deviation score (0 = normal, capped at 10)")
                        .register(meterRegistry);
            }
        }

        long durationMs = engine.getLastComputeDurationMs();
        if (durationMs > 0) {
            computeDuration.record(durationMs / 1000.0);
        }

        for (AnomalyEvent event : engine.getRecentAnomalies(Duration.ofMinutes(1))) {
            String counterKey = event.metricName() + ":" + event.severity().name().toLowerCase();
            anomalyCounters.computeIfAbsent(counterKey, k ->
                    Counter.builder("adaptive_anomaly_detected")
                            .tags("metric", event.metricName(), "severity", event.severity().name().toLowerCase())
                            .description("Number of anomalies detected")
                            .register(meterRegistry)
            );

        }
    }
}
