package com.monitoring.threshold.algorithm;

import java.util.OptionalInt;

public final class AutocorrelationAnalyzer {

    private AutocorrelationAnalyzer() {
    }

    public static double[] computeACF(double[] data, int maxLag) {
        int n = data.length;
        if (n == 0 || maxLag <= 0) {
            return new double[0];
        }

        double mean = 0.0;
        for (double v : data) {
            mean += v;
        }
        mean /= n;

        double variance = 0.0;
        for (double v : data) {
            variance += (v - mean) * (v - mean);
        }

        if (variance == 0.0) {
            double[] acf = new double[Math.min(maxLag + 1, n)];
            if (acf.length > 0) acf[0] = 1.0;
            return acf;
        }

        int effectiveMaxLag = Math.min(maxLag, n - 1);
        double[] acf = new double[effectiveMaxLag + 1];
        acf[0] = 1.0;

        for (int lag = 1; lag <= effectiveMaxLag; lag++) {
            double covariance = 0.0;
            for (int t = 0; t < n - lag; t++) {
                covariance += (data[t] - mean) * (data[t + lag] - mean);
            }
            acf[lag] = covariance / variance;
        }

        return acf;
    }

    public static OptionalInt detectSeasonLength(double[] data, int minSeasonLength, int maxSeasonLength) {
        if (data.length < maxSeasonLength * 2) {
            return OptionalInt.empty();
        }

        double[] acf = computeACF(data, maxSeasonLength);

        double bestAcf = 0.0;
        int bestLag = -1;

        for (int lag = minSeasonLength; lag < acf.length; lag++) {
            boolean isPeak = acf[lag] > acf[lag - 1];
            if (lag + 1 < acf.length) {
                isPeak = isPeak && acf[lag] >= acf[lag + 1];
            }

            if (isPeak && acf[lag] > 0.5 && acf[lag] > bestAcf) {
                bestAcf = acf[lag];
                bestLag = lag;
            }
        }

        return bestLag > 0 ? OptionalInt.of(bestLag) : OptionalInt.empty();
    }
}
