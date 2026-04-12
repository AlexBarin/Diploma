package com.monitoring.threshold.algorithm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveMovingStatsTest {

    @Test
    void constantValue_thresholdsShouldConvergeToNarrowBand() {
        AdaptiveMovingStats algo = new AdaptiveMovingStats(0.1, 3.0, 10);
        double constantValue = 50.0;

        for (int i = 0; i < 200; i++) {
            algo.update(constantValue, i);
        }

        ThresholdAlgorithm.ThresholdResult result = algo.currentThresholds();

        assertEquals(constantValue, result.midline(), 0.01,
                "Midline should be very close to the constant value");
        assertEquals(constantValue, result.upper(), 1.0,
                "Upper threshold should be near the constant value");
        assertEquals(constantValue, result.lower(), 1.0,
                "Lower threshold should be near the constant value");
        assertTrue(result.upper() >= result.lower(),
                "Upper must be >= lower");
    }

    @Test
    void randomWalk_thresholdsShouldAdapt() {
        AdaptiveMovingStats algo = new AdaptiveMovingStats(0.1, 3.0, 10);
        Random rng = new Random(42);

        double value = 100.0;
        for (int i = 0; i < 500; i++) {
            value += rng.nextGaussian() * 5.0;
            algo.update(value, i);
        }

        ThresholdAlgorithm.ThresholdResult result = algo.currentThresholds();
        assertTrue(result.upper() > result.lower(),
                "Upper threshold should be strictly greater than lower for noisy data");
        assertTrue(result.upper() > result.midline(),
                "Upper should be above midline");
        assertTrue(result.lower() < result.midline(),
                "Lower should be below midline");
    }

    @Test
    void stepFunction_shouldInitiallyDetectBreachThenAdapt() {
        AdaptiveMovingStats algo = new AdaptiveMovingStats(0.1, 3.0, 10);

        for (int i = 0; i < 200; i++) {
            algo.update(10.0, i);
        }

        ThresholdAlgorithm.ThresholdResult beforeStep = algo.currentThresholds();
        assertTrue(beforeStep.upper() < 100.0,
                "Before step, upper should be well below 100");

        assertTrue(100.0 > beforeStep.upper(),
                "Value 100 should breach the upper threshold established at 10");

        for (int i = 200; i < 700; i++) {
            algo.update(100.0, i);
        }

        ThresholdAlgorithm.ThresholdResult afterAdapt = algo.currentThresholds();
        assertEquals(100.0, afterAdapt.midline(), 5.0,
                "After adapting, midline should converge toward 100");
    }

    @Test
    void isReady_returnsFalseBeforeMinDataPointsTrueAfter() {
        int minDataPoints = 50;
        AdaptiveMovingStats algo = new AdaptiveMovingStats(0.1, 3.0, minDataPoints);

        for (int i = 0; i < minDataPoints - 1; i++) {
            algo.update(10.0, i);
            assertFalse(algo.isReady(),
                    "Should not be ready with only " + (i + 1) + " data points");
        }

        algo.update(10.0, minDataPoints - 1);
        assertTrue(algo.isReady(),
                "Should be ready after exactly minDataPoints updates");
    }

    @Test
    void compute_withDataPointList_producesReasonableResult() {
        AdaptiveMovingStats algo = new AdaptiveMovingStats(0.1, 3.0, 10);

        List<ThresholdAlgorithm.DataPoint> dataPoints = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            dataPoints.add(new ThresholdAlgorithm.DataPoint(i, 50.0));
        }

        ThresholdAlgorithm.ThresholdResult result = algo.compute(dataPoints, 3.0);
        assertEquals("adaptive-moving-stats", result.algorithmName());
        assertEquals(50.0, result.midline(), 1.0);
        assertTrue(result.upper() >= result.lower());
    }

    @Test
    void name_returnsCorrectAlgorithmName() {
        AdaptiveMovingStats algo = new AdaptiveMovingStats();
        assertEquals("adaptive-moving-stats", algo.name());
    }
}
