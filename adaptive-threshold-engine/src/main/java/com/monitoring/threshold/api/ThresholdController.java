package com.monitoring.threshold.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.monitoring.threshold.algorithm.ThresholdAlgorithm.ThresholdResult;
import com.monitoring.threshold.api.dto.AnomalyResponse;
import com.monitoring.threshold.api.dto.MetricAuditResponse;
import com.monitoring.threshold.api.dto.ThresholdResponse;
import com.monitoring.threshold.engine.AnomalyEvent;
import com.monitoring.threshold.engine.MetricState;
import com.monitoring.threshold.engine.ThresholdComputeEngine;

@RestController
@RequestMapping("/api/v1")
public class ThresholdController {

    private final ThresholdComputeEngine engine;

    public ThresholdController(ThresholdComputeEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/thresholds")
    public List<ThresholdResponse> getAllThresholds() {
        return engine.getMetricStates().values().stream()
                .filter(state -> state.getCurrentThresholdResult() != null)
                .map(this::toThresholdResponse)
                .toList();
    }

    @GetMapping("/thresholds/{metricName}")
    public ResponseEntity<ThresholdResponse> getThreshold(@PathVariable String metricName) {
        MetricState state = engine.getMetricState(metricName);
        if (state == null || state.getCurrentThresholdResult() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toThresholdResponse(state));
    }

    @GetMapping("/anomalies")
    public List<AnomalyResponse> getAnomalies(@RequestParam(defaultValue = "1h") String since) {
        Duration duration = parseDuration(since);
        return engine.getRecentAnomalies(duration).stream()
                .map(this::toAnomalyResponse)
                .toList();
    }

    @GetMapping("/metrics/audit")
    public List<MetricAuditResponse> getMetricAudit() {
        return engine.getMetricStates().values().stream()
                .map(this::toMetricAuditResponse)
                .toList();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "trackedMetrics", engine.getTrackedMetricCount(),
                "lastComputeTimestamp", engine.getLastComputeTimestamp(),
                "lastComputeDurationMs", engine.getLastComputeDurationMs(),
                "timestamp", Instant.now().toString()
        );
    }

    @PostMapping("/metrics/{name}/config")
    public ResponseEntity<Map<String, String>> updateMetricConfig(
            @PathVariable String name,
            @RequestBody Map<String, Object> body) {

        String algorithm = body.containsKey("algorithm") ? body.get("algorithm").toString() : null;
        Double sensitivity = body.containsKey("sensitivity")
                ? Double.parseDouble(body.get("sensitivity").toString()) : null;

        engine.updateMetricConfig(name, algorithm, sensitivity);

        return ResponseEntity.status(HttpStatus.OK)
                .body(Map.of("message", "Configuration updated for metric: " + name));
    }

    private ThresholdResponse toThresholdResponse(MetricState state) {
        ThresholdResult result = state.getCurrentThresholdResult();
        return new ThresholdResponse(
                state.getMetricName(),
                state.getJobName(),
                result != null ? result.algorithmName() : "none",
                result != null ? result.upper() : 0,
                result != null ? result.lower() : 0,
                result != null ? result.midline() : 0,
                state.getLastUpdated(),
                state.getDataPointCount()
        );
    }

    private AnomalyResponse toAnomalyResponse(AnomalyEvent event) {
        return new AnomalyResponse(
                event.metricName(),
                event.timestamp(),
                event.value(),
                event.thresholdBreached().name(),
                event.severity().name(),
                event.algorithmUsed(),
                event.deviationScore()
        );
    }

    private MetricAuditResponse toMetricAuditResponse(MetricState state) {
        ThresholdResult result = state.getCurrentThresholdResult();
        boolean isReady = state.getAlgorithm() != null && state.getAlgorithm().isReady();
        return new MetricAuditResponse(
                state.getMetricName(),
                state.getJobName(),
                state.getAlgorithm() != null ? state.getAlgorithm().name() : "unassigned",
                state.getDataPointCount(),
                isReady,
                result != null ? result.upper() : 0,
                result != null ? result.lower() : 0
        );
    }

    private static Duration parseDuration(String input) {
        if (input == null || input.isEmpty()) return Duration.ofHours(1);
        char unit = input.charAt(input.length() - 1);
        long value;
        try {
            value = Long.parseLong(input.substring(0, input.length() - 1));
        } catch (NumberFormatException e) {
            return Duration.ofHours(1);
        }
        return switch (unit) {
            case 's' -> Duration.ofSeconds(value);
            case 'm' -> Duration.ofMinutes(value);
            case 'h' -> Duration.ofHours(value);
            case 'd' -> Duration.ofDays(value);
            default -> Duration.ofHours(1);
        };
    }
}
