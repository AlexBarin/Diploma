package com.monitoring.threshold.algorithm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RobustQuantileBounds implements ThresholdAlgorithm {

    private final double sensitivity;
    private final int minDataPoints;

    private final PSquaredEstimator q1Estimator;
    private final PSquaredEstimator q3Estimator;
    private long count = 0;

    public RobustQuantileBounds(double sensitivity, int minDataPoints) {
        this.sensitivity = sensitivity;
        this.minDataPoints = minDataPoints;
        this.q1Estimator = new PSquaredEstimator(0.25);
        this.q3Estimator = new PSquaredEstimator(0.75);
    }

    public RobustQuantileBounds() {
        this(3.0, 100);
    }

    @Override
    public String name() {
        return "robust-quantile-bounds";
    }

    @Override
    public ThresholdResult compute(List<DataPoint> dataPoints, double sensitivity) {
        PSquaredEstimator localQ1 = new PSquaredEstimator(0.25);
        PSquaredEstimator localQ3 = new PSquaredEstimator(0.75);

        for (DataPoint dp : dataPoints) {
            localQ1.accept(dp.value());
            localQ3.accept(dp.value());
        }

        double q1 = localQ1.quantile();
        double q3 = localQ3.quantile();
        double iqr = q3 - q1;
        double midline = (q1 + q3) / 2.0;
        double factor = sensitivity * 1.5;

        return new ThresholdResult(q3 + factor * iqr, q1 - factor * iqr, midline, name());
    }

    @Override
    public void update(double value, long timestampEpochSeconds) {
        q1Estimator.accept(value);
        q3Estimator.accept(value);
        count++;
    }

    @Override
    public ThresholdResult currentThresholds() {
        double q1 = q1Estimator.quantile();
        double q3 = q3Estimator.quantile();
        double iqr = q3 - q1;
        double midline = (q1 + q3) / 2.0;
        double factor = sensitivity * 1.5;

        return new ThresholdResult(q3 + factor * iqr, q1 - factor * iqr, midline, name());
    }

    @Override
    public boolean isReady() {
        return count >= minDataPoints;
    }

    public long getCount() {
        return count;
    }

    @Override
    public boolean supportsSerialize() {
        return true;
    }

    @Override
    public String serializeState() {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("sensitivity", sensitivity);
            m.put("minDataPoints", minDataPoints);
            m.put("count", count);
            m.put("q1", q1Estimator.toMap());
            m.put("q3", q3Estimator.toMap());
            return new ObjectMapper().writeValueAsString(m);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    public static RobustQuantileBounds restoreFrom(String json) {
        try {
            Map<String, Object> m = new ObjectMapper().readValue(json, Map.class);
            double sensitivity = ((Number) m.get("sensitivity")).doubleValue();
            int minDataPoints = ((Number) m.get("minDataPoints")).intValue();
            RobustQuantileBounds instance = new RobustQuantileBounds(sensitivity, minDataPoints);
            instance.count = ((Number) m.get("count")).longValue();
            instance.q1Estimator.restoreFromMap((Map<String, Object>) m.get("q1"));
            instance.q3Estimator.restoreFromMap((Map<String, Object>) m.get("q3"));
            return instance;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to restore RobustQuantileBounds", e);
        }
    }

    static class PSquaredEstimator {

        private final double p;
        private final double[] q = new double[5];
        private final int[] n = new int[5];
        private final double[] nPrime = new double[5];
        private final double[] dn = new double[5];
        private int observationCount = 0;

        PSquaredEstimator(double p) {
            this.p = p;
            dn[0] = 0;
            dn[1] = p / 2.0;
            dn[2] = p;
            dn[3] = (1 + p) / 2.0;
            dn[4] = 1;
        }

        void accept(double x) {
            observationCount++;

            if (observationCount <= 5) {
                q[observationCount - 1] = x;
                if (observationCount == 5) {
                    java.util.Arrays.sort(q);
                    for (int i = 0; i < 5; i++) {
                        n[i] = i + 1;
                    }
                    nPrime[0] = 1;
                    nPrime[1] = 1 + 2 * p;
                    nPrime[2] = 1 + 4 * p;
                    nPrime[3] = 3 + 2 * p;
                    nPrime[4] = 5;
                }
                return;
            }

            int k;
            if (x < q[0]) {
                q[0] = x;
                k = 1;
            } else if (x < q[1]) {
                k = 1;
            } else if (x < q[2]) {
                k = 2;
            } else if (x < q[3]) {
                k = 3;
            } else if (x < q[4]) {
                k = 4;
            } else {
                q[4] = x;
                k = 4;
            }

            for (int i = k; i < 5; i++) {
                n[i]++;
            }

            for (int i = 0; i < 5; i++) {
                nPrime[i] += dn[i];
            }

            for (int i = 1; i <= 3; i++) {
                double d = nPrime[i] - n[i];
                if ((d >= 1 && n[i + 1] - n[i] > 1) || (d <= -1 && n[i - 1] - n[i] < -1)) {
                    int sign = d >= 0 ? 1 : -1;
                    double qNew = parabolic(i, sign);

                    if (q[i - 1] < qNew && qNew < q[i + 1]) {
                        q[i] = qNew;
                    } else {
                        q[i] = linear(i, sign);
                    }
                    n[i] += sign;
                }
            }
        }

        private double parabolic(int i, int sign) {
            double qi = q[i];
            double qim1 = q[i - 1];
            double qip1 = q[i + 1];
            int ni = n[i];
            int nim1 = n[i - 1];
            int nip1 = n[i + 1];

            double term1 = (double) sign / (nip1 - nim1);
            double left = (ni - nim1 + sign) * (qip1 - qi) / (nip1 - ni);
            double right = (nip1 - ni - sign) * (qi - qim1) / (ni - nim1);
            return qi + term1 * (left + right);
        }

        private double linear(int i, int sign) {
            int idx = i + sign;
            return q[i] + sign * (q[idx] - q[i]) / (n[idx] - n[i]);
        }

        double quantile() {
            if (observationCount < 5) {
                double[] sorted = java.util.Arrays.copyOf(q, observationCount);
                java.util.Arrays.sort(sorted);
                int idx = (int) (p * (sorted.length - 1));
                return sorted[Math.min(idx, sorted.length - 1)];
            }
            return q[2];
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("p", p);
            m.put("q", q.clone());
            m.put("n", n.clone());
            m.put("nPrime", nPrime.clone());
            m.put("dn", dn.clone());
            m.put("observationCount", observationCount);
            return m;
        }

        @SuppressWarnings("unchecked")
        void restoreFromMap(Map<String, Object> m) {
            List<Number> qList = (List<Number>) m.get("q");
            List<Number> nList = (List<Number>) m.get("n");
            List<Number> npList = (List<Number>) m.get("nPrime");
            List<Number> dnList = (List<Number>) m.get("dn");
            for (int i = 0; i < 5; i++) {
                q[i] = qList.get(i).doubleValue();
                n[i] = nList.get(i).intValue();
                nPrime[i] = npList.get(i).doubleValue();
                dn[i] = dnList.get(i).doubleValue();
            }
            observationCount = ((Number) m.get("observationCount")).intValue();
        }
    }
}
