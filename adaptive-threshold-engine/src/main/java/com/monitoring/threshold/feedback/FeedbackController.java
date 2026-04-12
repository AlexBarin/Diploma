package com.monitoring.threshold.feedback;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.monitoring.threshold.config.ThresholdEngineProperties;
import com.monitoring.threshold.feedback.SensitivityTuner.AdjustmentResult;
import com.monitoring.threshold.persistence.AnomalyRepository;
import com.monitoring.threshold.persistence.AnomalyRepository.AnomalyRow;
import com.monitoring.threshold.persistence.FeedbackAdjustmentRepository;

@RestController
@RequestMapping("/api/v1")
public class FeedbackController {

    private static final Set<String> VALID_LABELS = Set.of("TRUE_POSITIVE", "FALSE_POSITIVE", "EXPECTED");

    private final AnomalyRepository anomalyRepo;
    private final FeedbackAdjustmentRepository adjustmentRepo;
    private final SensitivityTuner tuner;
    private final ThresholdEngineProperties properties;

    public FeedbackController(AnomalyRepository anomalyRepo,
                               FeedbackAdjustmentRepository adjustmentRepo,
                               SensitivityTuner tuner,
                               ThresholdEngineProperties properties) {
        this.anomalyRepo = anomalyRepo;
        this.adjustmentRepo = adjustmentRepo;
        this.tuner = tuner;
        this.properties = properties;
    }

    @PutMapping("/anomalies/{id}/label")
    public ResponseEntity<?> labelAnomaly(@PathVariable long id, @RequestBody Map<String, String> body) {
        String label = body.get("label");
        if (label == null || !VALID_LABELS.contains(label)) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Label must be one of: " + VALID_LABELS));
        }

        AnomalyRow row = anomalyRepo.findById(id);
        if (row == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Anomaly " + id + " not found"));
        }

        anomalyRepo.setLabel(id, label);
        tuner.evaluate(row.metricName());

        return ResponseEntity.ok(Map.of("message", "Anomaly " + id + " labeled as " + label));
    }

    @GetMapping("/anomalies/unlabeled")
    public List<AnomalyRow> getUnlabeled(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "0") int page) {
        return anomalyRepo.findUnlabeled(size, page * size);
    }

    @GetMapping("/anomalies/history")
    public List<AnomalyRow> getHistory(
            @RequestParam(required = false) String metricName,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "0") int page) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        return anomalyRepo.findRecent(metricName, severity, since, size, page * size);
    }

    @GetMapping("/feedback/stats/{metricName}")
    public ResponseEntity<?> getStats(@PathVariable String metricName) {
        Instant since = Instant.now().minus(14, ChronoUnit.DAYS);
        List<AnomalyRow> labeled = anomalyRepo.findLabeledByMetricSince(metricName, since);
        return ResponseEntity.ok(computeStats(metricName, labeled));
    }

    @GetMapping("/feedback/stats")
    public List<Map<String, Object>> getAllStats() {
        Instant since = Instant.now().minus(14, ChronoUnit.DAYS);
        List<String> metrics = anomalyRepo.findDistinctLabeledMetrics(since);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String metric : metrics) {
            List<AnomalyRow> labeled = anomalyRepo.findLabeledByMetricSince(metric, since);
            result.add(computeStats(metric, labeled));
        }
        return result;
    }

    @GetMapping("/feedback/adjustments")
    public List<FeedbackAdjustmentRepository.AdjustmentRow> getAdjustments() {
        return adjustmentRepo.findAll();
    }

    @PostMapping("/feedback/recalculate")
    public Map<String, Object> recalculate() {
        Instant since = Instant.now().minus(14, ChronoUnit.DAYS);
        List<String> metrics = anomalyRepo.findDistinctLabeledMetrics(since);
        List<AdjustmentResult> adjusted = new ArrayList<>();
        for (String metric : metrics) {
            Optional<AdjustmentResult> result = tuner.evaluate(metric);
            result.ifPresent(adjusted::add);
        }
        return Map.of("metricsEvaluated", metrics.size(), "adjustments", adjusted);
    }

    private Map<String, Object> computeStats(String metricName, List<AnomalyRow> labeled) {
        int tp = 0, fp = 0, ex = 0;
        for (AnomalyRow row : labeled) {
            if (row.operatorLabel() == null) continue;
            switch (row.operatorLabel()) {
                case "TRUE_POSITIVE" -> tp++;
                case "FALSE_POSITIVE" -> fp++;
                case "EXPECTED" -> ex++;
            }
        }
        double fpr = (tp + fp) > 0 ? (double) fp / (tp + fp) : 0.0;

        ThresholdEngineProperties.MetricOverride override = properties.getMetrics().get(metricName);
        double sensitivity = (override != null && override.getSensitivity() != null)
                ? override.getSensitivity()
                : properties.getDefaults().getSensitivity();

        return Map.of(
                "metricName", metricName,
                "truePositives", tp,
                "falsePositives", fp,
                "expected", ex,
                "totalLabeled", tp + fp + ex,
                "falsePositiveRatio", fpr,
                "currentSensitivity", sensitivity);
    }
}
