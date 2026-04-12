package com.monitoring.threshold.algorithm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HoltWintersSmoothingTest {

    @Test
    void sineWave_afterWarmup_thresholdsShouldTrackTheWave() {
        int seasonLength = 24;
        int minDataPoints = seasonLength * 3;
        HoltWintersSmoothing algo = new HoltWintersSmoothing(seasonLength, 3.0, minDataPoints);

        int totalPoints = seasonLength * 10;
        for (int i = 0; i < totalPoints; i++) {
            double value = 100.0 + 20.0 * Math.sin(2.0 * Math.PI * i / seasonLength);
            algo.update(value, i);
        }

        assertTrue(algo.isReady(), "Should be ready after sufficient data");

        ThresholdAlgorithm.ThresholdResult result = algo.currentThresholds();
        assertTrue(result.midline() > 50.0 && result.midline() < 150.0,
                "Midline should be within the range of the sine wave signal");
        assertTrue(result.upper() > result.lower(),
                "Upper should exceed lower");
    }

    @Test
    void linearTrend_shouldPredictIncreasingValues() {
        int seasonLength = 12;
        int minDataPoints = seasonLength * 3;
        HoltWintersSmoothing algo = new HoltWintersSmoothing(seasonLength, 3.0, minDataPoints);

        int totalPoints = seasonLength * 8;
        for (int i = 0; i < totalPoints; i++) {
            double seasonal = 5.0 * Math.sin(2.0 * Math.PI * i / seasonLength);
            double value = 50.0 + 0.5 * i + seasonal;
            algo.update(value, i);
        }

        assertTrue(algo.isReady(), "Should be ready after sufficient data");

        ThresholdAlgorithm.ThresholdResult result = algo.currentThresholds();
        assertTrue(result.midline() > 50.0,
                "Midline should reflect the upward trend and be above the initial value");
    }

    @Test
    void seasonalPlusTrend_combined() {
        int seasonLength = 10;
        int minDataPoints = seasonLength * 3;
        HoltWintersSmoothing algo = new HoltWintersSmoothing(seasonLength, 3.0, minDataPoints);

        int totalPoints = seasonLength * 12;
        for (int i = 0; i < totalPoints; i++) {
            double trend = 0.3 * i;
            double seasonal = 15.0 * Math.sin(2.0 * Math.PI * i / seasonLength);
            double value = 100.0 + trend + seasonal;
            algo.update(value, i);
        }

        assertTrue(algo.isReady(), "Should be ready after sufficient data");

        ThresholdAlgorithm.ThresholdResult result = algo.currentThresholds();
        assertTrue(result.upper() > result.lower(), "Upper should exceed lower");
        assertNotEquals(0.0, result.midline(), "Midline should not be zero");
    }

    @Test
    void compute_withDataPointList_sineWave() {
        int seasonLength = 20;
        HoltWintersSmoothing algo = new HoltWintersSmoothing(seasonLength, 3.0, 10);

        List<ThresholdAlgorithm.DataPoint> dataPoints = new ArrayList<>();
        for (int i = 0; i < seasonLength * 5; i++) {
            double value = 100.0 + 10.0 * Math.sin(2.0 * Math.PI * i / seasonLength);
            dataPoints.add(new ThresholdAlgorithm.DataPoint(i, value));
        }

        ThresholdAlgorithm.ThresholdResult result = algo.compute(dataPoints, 3.0);
        assertEquals("holt-winters", result.algorithmName());
        assertTrue(result.upper() > result.lower(),
                "Upper should be greater than lower");
        assertTrue(result.midline() > 50.0 && result.midline() < 150.0,
                "Midline should be in a reasonable range");
    }

    @Test
    void isReady_requiresInitializationAndMinDataPoints() {
        int seasonLength = 10;
        int minDataPoints = 30;
        HoltWintersSmoothing algo = new HoltWintersSmoothing(seasonLength, 3.0, minDataPoints);

        for (int i = 0; i < seasonLength * 2 - 1; i++) {
            algo.update(50.0 + Math.sin(2.0 * Math.PI * i / seasonLength), i);
            assertFalse(algo.isReady(),
                    "Should not be ready before initialization (step " + i + ")");
        }

        for (int i = seasonLength * 2 - 1; i < minDataPoints; i++) {
            algo.update(50.0 + Math.sin(2.0 * Math.PI * i / seasonLength), i);
        }
        assertTrue(algo.isReady(), "Should be ready after minDataPoints with initialization");
    }

    @Test
    void name_returnsCorrectAlgorithmName() {
        HoltWintersSmoothing algo = new HoltWintersSmoothing(24, 3.0, 72);
        assertEquals("holt-winters", algo.name());
    }

    @Test
    void compute_insufficientData_fallsBackToSimpleStats() {
        int seasonLength = 24;
        HoltWintersSmoothing algo = new HoltWintersSmoothing(seasonLength, 3.0, 10);

        List<ThresholdAlgorithm.DataPoint> dataPoints = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            dataPoints.add(new ThresholdAlgorithm.DataPoint(i, 50.0));
        }

        ThresholdAlgorithm.ThresholdResult result = algo.compute(dataPoints, 3.0);
        assertEquals("holt-winters", result.algorithmName());
        assertEquals(50.0, result.midline(), 0.01);
    }
}
