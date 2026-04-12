package com.monitoring.threshold.algorithm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RobustQuantileBoundsTest {

    @Test
    void normallyDistributedData_boundsShouldBeApproximatelyCorrect() {
        RobustQuantileBounds algo = new RobustQuantileBounds(1.0, 10);
        Random rng = new Random(42);
        double mean = 100.0;
        double stddev = 10.0;

        int n = 10000;
        for (int i = 0; i < n; i++) {
            double value = mean + rng.nextGaussian() * stddev;
            algo.update(value, i);
        }

        assertTrue(algo.isReady(), "Should be ready after 10000 points");

        ThresholdAlgorithm.ThresholdResult result = algo.currentThresholds();

        double expectedUpper = mean + 2.7 * stddev;
        double expectedLower = mean - 2.7 * stddev;

        assertEquals(expectedUpper, result.upper(), 5.0,
                "Upper bound should be approximately mean + 2.7*sigma");
        assertEquals(expectedLower, result.lower(), 5.0,
                "Lower bound should be approximately mean - 2.7*sigma");
        assertEquals(mean, result.midline(), 3.0,
                "Midline should be approximately the mean for symmetric distribution");
    }

    @Test
    void dataWithOutliers_boundsShouldBeRobust() {
        RobustQuantileBounds algo = new RobustQuantileBounds(1.0, 10);
        Random rng = new Random(123);

        int n = 5000;
        for (int i = 0; i < n; i++) {
            double value;
            if (i % 100 == 0) {
                value = 100.0 + rng.nextGaussian() * 500.0;
            } else {
                value = 100.0 + rng.nextGaussian() * 10.0;
            }
            algo.update(value, i);
        }

        ThresholdAlgorithm.ThresholdResult result = algo.currentThresholds();

        assertTrue(result.upper() < 200.0,
                "Upper bound should not be pulled to extreme by outliers, got: " + result.upper());
        assertTrue(result.lower() > 0.0,
                "Lower bound should not be pulled to extreme by outliers, got: " + result.lower());
    }

    @Test
    void skewedDistribution_asymmetricBounds() {
        RobustQuantileBounds algo = new RobustQuantileBounds(1.0, 10);
        Random rng = new Random(99);

        int n = 10000;
        for (int i = 0; i < n; i++) {
            double value = 50.0 + 20.0 * (-Math.log(rng.nextDouble()));
            algo.update(value, i);
        }

        ThresholdAlgorithm.ThresholdResult result = algo.currentThresholds();

        double upperDistance = result.upper() - result.midline();
        double lowerDistance = result.midline() - result.lower();

        assertTrue(upperDistance > 0, "Upper should be above midline");
        assertTrue(lowerDistance > 0, "Lower should be below midline");

        assertTrue(result.upper() > result.lower(), "Upper should exceed lower");
    }

    @Test
    void compute_withDataPointList() {
        RobustQuantileBounds algo = new RobustQuantileBounds(1.0, 10);
        Random rng = new Random(42);

        List<ThresholdAlgorithm.DataPoint> dataPoints = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            dataPoints.add(new ThresholdAlgorithm.DataPoint(i, 100.0 + rng.nextGaussian() * 10.0));
        }

        ThresholdAlgorithm.ThresholdResult result = algo.compute(dataPoints, 1.0);
        assertEquals("robust-quantile-bounds", result.algorithmName());
        assertTrue(result.upper() > result.lower());
        assertEquals(100.0, result.midline(), 5.0);
    }

    @Test
    void isReady_respectsMinDataPoints() {
        int minDataPoints = 200;
        RobustQuantileBounds algo = new RobustQuantileBounds(1.0, minDataPoints);

        for (int i = 0; i < minDataPoints - 1; i++) {
            algo.update(50.0, i);
            assertFalse(algo.isReady());
        }

        algo.update(50.0, minDataPoints - 1);
        assertTrue(algo.isReady());
    }

    @Test
    void name_returnsCorrectAlgorithmName() {
        RobustQuantileBounds algo = new RobustQuantileBounds();
        assertEquals("robust-quantile-bounds", algo.name());
    }
}
