package com.monitoring.threshold.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.monitoring.threshold.algorithm.AdaptiveMovingStats;
import com.monitoring.threshold.algorithm.HoltWintersSmoothing;
import com.monitoring.threshold.algorithm.RateOfChangeDetector;
import com.monitoring.threshold.algorithm.RobustQuantileBounds;
import com.monitoring.threshold.algorithm.ThresholdAlgorithm;
import com.monitoring.threshold.engine.MetricState;
import com.monitoring.threshold.engine.ThresholdComputeEngine;
import com.monitoring.threshold.persistence.AlgorithmStateRepository.AlgorithmStateRow;

import jakarta.annotation.PreDestroy;

@Service
public class AlgorithmStateService {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmStateService.class);
    private static final Duration MAX_STATE_AGE = Duration.ofHours(1);

    private final AlgorithmStateRepository repository;
    private final ThresholdComputeEngine engine;

    public AlgorithmStateService(AlgorithmStateRepository repository,
                                  @Lazy ThresholdComputeEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }

    public Optional<ThresholdAlgorithm> restore(String metricKey) {
        try {
            Optional<AlgorithmStateRow> row = repository.findByKey(metricKey);
            if (row.isEmpty()) {
                return Optional.empty();
            }

            AlgorithmStateRow state = row.get();
            if (state.savedAt().plus(MAX_STATE_AGE).isBefore(Instant.now())) {
                log.debug("Saved state for '{}' is too old ({}), ignoring", metricKey, state.savedAt());
                return Optional.empty();
            }

            ThresholdAlgorithm algorithm = deserialize(state.algorithmName(), state.stateJson());
            return Optional.of(algorithm);
        } catch (Exception e) {
            log.warn("Failed to restore algorithm state for '{}': {}", metricKey, e.getMessage());
            return Optional.empty();
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void periodicSave() {
        saveAll(engine.getMetricStates());
    }

    @PreDestroy
    public void shutdownSave() {
        try {
            log.info("Saving algorithm states before shutdown...");
            saveAll(engine.getMetricStates());
        } catch (Exception e) {
            log.warn("Could not save algorithm states on shutdown: {}", e.getMessage());
        }
    }

    public void saveAll(Map<String, MetricState> states) {
        int saved = 0;
        for (Map.Entry<String, MetricState> entry : states.entrySet()) {
            MetricState state = entry.getValue();
            ThresholdAlgorithm algorithm = state.getAlgorithm();
            if (algorithm == null || !algorithm.supportsSerialize()) {
                continue;
            }
            try {
                String json = algorithm.serializeState();
                repository.save(entry.getKey(), algorithm.name(), json);
                saved++;
            } catch (Exception e) {
                log.warn("Failed to save state for '{}': {}", entry.getKey(), e.getMessage());
            }
        }
        if (saved > 0) {
            log.info("Saved algorithm state for {} metrics", saved);
        }
    }

    private static ThresholdAlgorithm deserialize(String algorithmName, String json) {
        String baseName = algorithmName.contains("+") ? algorithmName.substring(0, algorithmName.indexOf('+')) : algorithmName;
        return switch (baseName) {
            case "adaptive-moving-stats" -> AdaptiveMovingStats.restoreFrom(json);
            case "holt-winters" -> HoltWintersSmoothing.restoreFrom(json);
            case "robust-quantile-bounds" -> RobustQuantileBounds.restoreFrom(json);
            case "rate-of-change" -> RateOfChangeDetector.restoreFrom(json);
            default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
        };
    }
}
