package com.monitoring.threshold.feedback;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.monitoring.threshold.config.ThresholdEngineProperties;
import com.monitoring.threshold.engine.ThresholdComputeEngine;
import com.monitoring.threshold.persistence.AnomalyRepository;
import com.monitoring.threshold.persistence.AnomalyRepository.AnomalyRow;
import com.monitoring.threshold.persistence.FeedbackAdjustmentRepository;

@Component
public class SensitivityTuner {

    private static final Logger log = LoggerFactory.getLogger(SensitivityTuner.class);

    private static final int EVALUATION_WINDOW_DAYS = 14;
    private static final int MIN_LABELS = 10;
    private static final double ADJUSTMENT_STEP = 0.25;
    private static final double MIN_SENSITIVITY = 1.5;
    private static final double MAX_SENSITIVITY = 5.0;
    private static final double FDR_UPPER = 0.50;
    private static final double FDR_LOWER = 0.15;

    private final AnomalyRepository anomalyRepo;
    private final FeedbackAdjustmentRepository adjustmentRepo;
    private final ThresholdComputeEngine engine;
    private final ThresholdEngineProperties properties;

    public SensitivityTuner(AnomalyRepository anomalyRepo,
                             FeedbackAdjustmentRepository adjustmentRepo,
                             ThresholdComputeEngine engine,
                             ThresholdEngineProperties properties) {
        this.anomalyRepo = anomalyRepo;
        this.adjustmentRepo = adjustmentRepo;
        this.engine = engine;
        this.properties = properties;
    }

    public Optional<AdjustmentResult> evaluate(String metricName) {
        Instant since = Instant.now().minus(EVALUATION_WINDOW_DAYS, ChronoUnit.DAYS);
        List<AnomalyRow> labeled = anomalyRepo.findLabeledByMetricSince(metricName, since);

        int tp = 0, fp = 0, ex = 0;
        for (AnomalyRow row : labeled) {
            switch (row.operatorLabel()) {
                case "TRUE_POSITIVE" -> tp++;
                case "FALSE_POSITIVE" -> fp++;
                case "EXPECTED" -> ex++;
            }
        }

        if ((tp + fp) < MIN_LABELS) {
            return Optional.empty();
        }

        double fdr = (double) fp / (tp + fp);

        ThresholdEngineProperties.MetricOverride override = properties.getMetrics().get(metricName);
        double currentSensitivity = (override != null && override.getSensitivity() != null)
                ? override.getSensitivity()
                : properties.getDefaults().getSensitivity();

        double newSensitivity;
        String direction;

        if (fdr > FDR_UPPER) {
            newSensitivity = currentSensitivity + ADJUSTMENT_STEP;
            direction = "WIDEN";
        } else if (fdr < FDR_LOWER && currentSensitivity > MIN_SENSITIVITY) {
            newSensitivity = currentSensitivity - (ADJUSTMENT_STEP / 2.0);
            direction = "TIGHTEN";
        } else {
            return Optional.empty();
        }

        newSensitivity = Math.max(MIN_SENSITIVITY, Math.min(MAX_SENSITIVITY, newSensitivity));
        if (newSensitivity == currentSensitivity) {
            return Optional.empty();
        }

        engine.updateMetricConfig(metricName, null, newSensitivity);
        adjustmentRepo.save(metricName, currentSensitivity, newSensitivity, fdr, tp, fp, ex, direction);

        log.info("Adjusted sensitivity for '{}': {} -> {} (FDR={}, direction={})",
                metricName, currentSensitivity, newSensitivity,
                String.format("%.2f", fdr), direction);

        return Optional.of(new AdjustmentResult(metricName, currentSensitivity, newSensitivity, fdr, direction));
    }

    public record AdjustmentResult(String metricName, double oldSensitivity,
                                    double newSensitivity, double fdr, String direction) {
    }
}
