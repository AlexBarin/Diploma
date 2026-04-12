package com.monitoring.threshold.algorithm;

import java.util.OptionalInt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.monitoring.threshold.config.ThresholdEngineProperties;

@Component
public class AlgorithmSelector {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmSelector.class);

    public ThresholdAlgorithm selectAlgorithm(String metricName, double[] recentData,
                                               ThresholdEngineProperties properties) {
        ThresholdEngineProperties.Defaults defaults = properties.getDefaults();
        double sensitivity = defaults.getSensitivity();
        int minDataPoints = defaults.getMinDataPoints();

        ThresholdEngineProperties.MetricOverride override = properties.getMetrics().get(metricName);
        if (override != null && override.getAlgorithm() != null) {
            Double overrideSensitivity = override.getSensitivity() != null
                    ? override.getSensitivity() : sensitivity;
            return createAlgorithmByName(override.getAlgorithm(), overrideSensitivity,
                    defaults.getEmaAlpha(), minDataPoints,
                    override.getSeasonLength() != null ? override.getSeasonLength() : 12);
        }

        if (recentData == null || recentData.length < 20) {
            log.debug("Not enough data for metric '{}', using default algorithm", metricName);
            return new AdaptiveMovingStats(defaults.getEmaAlpha(), sensitivity, minDataPoints);
        }

        int minSeason = 6;
        int maxSeason = Math.min(recentData.length / 2, 288);
        OptionalInt seasonLength = AutocorrelationAnalyzer.detectSeasonLength(recentData, minSeason, maxSeason);
        if (seasonLength.isPresent()) {
            log.info("Detected seasonality in metric '{}' with period={}", metricName, seasonLength.getAsInt());
            return new HoltWintersSmoothing(seasonLength.getAsInt(), sensitivity, minDataPoints);
        }

        double skewness = computeSkewness(recentData);
        if (Math.abs(skewness) > 1.0) {
            log.info("High skewness ({}) in metric '{}', using quantile-based bounds", skewness, metricName);
            return new RobustQuantileBounds(sensitivity, minDataPoints);
        }

        if (isMonotonic(recentData)) {
            log.info("Monotonic trend detected in metric '{}', using rate-of-change detector", metricName);
            return new RateOfChangeDetector(defaults.getEmaAlpha(), sensitivity, minDataPoints);
        }

        return new AdaptiveMovingStats(defaults.getEmaAlpha(), sensitivity, minDataPoints);
    }

    private ThresholdAlgorithm createAlgorithmByName(String name, double sensitivity,
                                                      double emaAlpha, int minDataPoints,
                                                      int seasonLength) {
        return switch (name) {
            case "holt-winters" -> new HoltWintersSmoothing(seasonLength, sensitivity, minDataPoints);
            case "robust-quantile-bounds" -> new RobustQuantileBounds(sensitivity, minDataPoints);
            case "rate-of-change" -> new RateOfChangeDetector(emaAlpha, sensitivity, minDataPoints);
            default -> new AdaptiveMovingStats(emaAlpha, sensitivity, minDataPoints);
        };
    }

    private static double computeSkewness(double[] data) {
        int n = data.length;
        if (n < 3) return 0.0;

        double mean = 0.0;
        for (double v : data) mean += v;
        mean /= n;

        double m2 = 0.0;
        double m3 = 0.0;
        for (double v : data) {
            double diff = v - mean;
            m2 += diff * diff;
            m3 += diff * diff * diff;
        }
        m2 /= n;
        m3 /= n;

        double stddev = Math.sqrt(m2);
        if (stddev == 0.0) return 0.0;

        return m3 / (stddev * stddev * stddev);
    }

    private static boolean isMonotonic(double[] data) {
        if (data.length < 10) return false;

        int increasing = 0;
        int decreasing = 0;
        for (int i = 1; i < data.length; i++) {
            if (data[i] > data[i - 1]) increasing++;
            else if (data[i] < data[i - 1]) decreasing++;
        }

        int total = data.length - 1;
        return increasing > total * 0.9 || decreasing > total * 0.9;
    }
}
