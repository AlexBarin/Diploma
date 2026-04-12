package com.monitoring.threshold.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.monitoring.threshold.algorithm.ThresholdAlgorithm.DataPoint;

public class OutlierFilter {

    private static final double MAD_FACTOR = 5.0;
    private static final double MAD_TO_SIGMA = 1.4826;

    public static List<DataPoint> removeExtremeOutliers(List<DataPoint> dataPoints) {
        if (dataPoints.size() < 10) {
            return dataPoints;
        }

        double[] values = dataPoints.stream().mapToDouble(DataPoint::value).toArray();
        double median = median(values);
        double mad = mad(values, median);

        if (mad < 1e-15) {
            return dataPoints;
        }

        double threshold = MAD_FACTOR * MAD_TO_SIGMA * mad;
        List<DataPoint> filtered = new ArrayList<>();
        for (DataPoint dp : dataPoints) {
            if (Math.abs(dp.value() - median) <= threshold) {
                filtered.add(dp);
            }
        }

        if (filtered.size() < dataPoints.size() * 0.8) {
            return dataPoints;
        }

        return filtered;
    }

    public static DriftResult detectDrift(List<DataPoint> dataPoints) {
        if (dataPoints.size() < 20) {
            return new DriftResult(false, 0.0, 0.0, 0.0);
        }

        int quarter = dataPoints.size() / 4;
        double[] firstQ = dataPoints.subList(0, quarter).stream()
                .mapToDouble(DataPoint::value).toArray();
        double[] lastQ = dataPoints.subList(dataPoints.size() - quarter, dataPoints.size()).stream()
                .mapToDouble(DataPoint::value).toArray();

        double firstMedian = median(firstQ);
        double lastMedian = median(lastQ);

        double[] allValues = dataPoints.stream().mapToDouble(DataPoint::value).toArray();
        double fullMad = mad(allValues, median(allValues));

        if (fullMad < 1e-15) {
            return new DriftResult(false, firstMedian, lastMedian, 0.0);
        }

        double driftMagnitude = (lastMedian - firstMedian) / (MAD_TO_SIGMA * fullMad);

        boolean isMonotonic = isConsistentlyMonotonic(dataPoints, 0.70);
        boolean isDrifting = Math.abs(driftMagnitude) > 2.0 && isMonotonic;

        return new DriftResult(isDrifting, firstMedian, lastMedian, driftMagnitude);
    }

    private static boolean isConsistentlyMonotonic(List<DataPoint> dataPoints, double threshold) {
        int increasing = 0;
        int total = 0;
        for (int i = 4; i < dataPoints.size(); i += 4) {
            total++;
            if (dataPoints.get(i).value() > dataPoints.get(i - 4).value()) {
                increasing++;
            }
        }
        if (total == 0) return false;
        double ratio = (double) increasing / total;
        return ratio > threshold || ratio < (1.0 - threshold);
    }

    private static double median(double[] values) {
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 0) {
            return (sorted[mid - 1] + sorted[mid]) / 2.0;
        }
        return sorted[mid];
    }

    private static double mad(double[] values, double median) {
        double[] deviations = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            deviations[i] = Math.abs(values[i] - median);
        }
        return median(deviations);
    }

    public record DriftResult(boolean driftDetected, double baselineMedian, double recentMedian,
                               double driftMagnitude) {
        public boolean isUpwardDrift() {
            return driftDetected && recentMedian > baselineMedian;
        }
    }
}
