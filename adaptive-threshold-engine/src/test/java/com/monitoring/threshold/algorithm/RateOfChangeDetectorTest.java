package com.monitoring.threshold.algorithm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateOfChangeDetectorTest {

    @Test
    void steadyGrowth_constantRate_noAnomaly() {
        RateOfChangeDetector algo = new RateOfChangeDetector(0.1, 3.0, 10);

        double rate = 2.0;
        for (int i = 0; i < 500; i++) {
            double value = 100.0 + rate * i;
            algo.update(value, i);
        }

        assertTrue(algo.isReady(), "Should be ready after 500 points");

        ThresholdAlgorithm.ThresholdResult result = algo.currentThresholds();

        double lastValue = 100.0 + rate * 499;
        assertEquals(lastValue, result.midline(), 0.01,
                "Midline should be the current value");

        double spread = result.upper() - result.lower();
        assertTrue(spread < 50.0,
                "Spread should be small for constant rate, got: " + spread);
    }

    @Test
    void suddenSpike_shouldDetect() {
        RateOfChangeDetector algo = new RateOfChangeDetector(0.1, 3.0, 10);

        for (int i = 0; i < 200; i++) {
            algo.update(100.0 + 0.5 * i, i);
        }

        ThresholdAlgorithm.ThresholdResult beforeSpike = algo.currentThresholds();

        double spikeValue = 100.0 + 0.5 * 200 + 500.0;
        algo.update(spikeValue, 200);

        assertTrue(spikeValue > beforeSpike.upper(),
                "Spike value (" + spikeValue + ") should exceed pre-spike upper threshold (" + beforeSpike.upper() + ")");
    }

    @Test
    void decreasingMetric_suddenAcceleration() {
        RateOfChangeDetector algo = new RateOfChangeDetector(0.1, 3.0, 10);

        for (int i = 0; i < 300; i++) {
            double value = 1000.0 - 0.5 * i;
            algo.update(value, i);
        }

        ThresholdAlgorithm.ThresholdResult beforeAccel = algo.currentThresholds();

        double dropValue = 1000.0 - 0.5 * 300 - 200.0;
        algo.update(dropValue, 300);

        assertTrue(dropValue < beforeAccel.lower(),
                "Drop value (" + dropValue + ") should be below pre-acceleration lower threshold (" + beforeAccel.lower() + ")");
    }

    @Test
    void compute_withDataPointList() {
        RateOfChangeDetector algo = new RateOfChangeDetector(0.1, 3.0, 10);

        List<ThresholdAlgorithm.DataPoint> dataPoints = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            dataPoints.add(new ThresholdAlgorithm.DataPoint(i, 50.0 + 0.3 * i));
        }

        ThresholdAlgorithm.ThresholdResult result = algo.compute(dataPoints, 3.0);
        assertEquals("rate-of-change", result.algorithmName());
        assertTrue(result.upper() > result.lower(), "Upper should exceed lower");
    }

    @Test
    void isReady_requiresMinDataPointsAndInitialization() {
        int minDataPoints = 50;
        RateOfChangeDetector algo = new RateOfChangeDetector(0.1, 3.0, minDataPoints);

        algo.update(10.0, 0);
        assertFalse(algo.isReady(), "Should not be ready with just one point");

        for (int i = 1; i < minDataPoints - 1; i++) {
            algo.update(10.0 + i, i);
            assertFalse(algo.isReady(),
                    "Should not be ready with only " + (i + 1) + " data points");
        }

        algo.update(10.0 + minDataPoints - 1, minDataPoints - 1);
        assertTrue(algo.isReady(), "Should be ready after minDataPoints with derivative initialized");
    }

    @Test
    void compute_insufficientData_returnsMaxBounds() {
        RateOfChangeDetector algo = new RateOfChangeDetector();

        List<ThresholdAlgorithm.DataPoint> dataPoints = new ArrayList<>();
        dataPoints.add(new ThresholdAlgorithm.DataPoint(0, 10.0));

        ThresholdAlgorithm.ThresholdResult result = algo.compute(dataPoints, 3.0);
        assertEquals(Double.MAX_VALUE, result.upper());
        assertEquals(-Double.MAX_VALUE, result.lower());
    }

    @Test
    void name_returnsCorrectAlgorithmName() {
        RateOfChangeDetector algo = new RateOfChangeDetector();
        assertEquals("rate-of-change", algo.name());
    }
}
