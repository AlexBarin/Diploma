package com.monitoring.threshold.algorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HoltWintersSmoothing implements ThresholdAlgorithm {

    private final double alpha;
    private final double beta;
    private final double gamma;
    private final int seasonLength;
    private final double sensitivity;
    private final int minDataPoints;

    private double level = 0.0;
    private double trend = 0.0;
    private final double[] seasonal;
    private int seasonIndex = 0;
    private long count = 0;
    private boolean initialized = false;
    private boolean useMultiplicative = true;

    private double residualMu = 0.0;
    private double residualSigma = 0.0;
    private static final double RESIDUAL_ALPHA = 0.1;

    private final List<Double> initBuffer = new ArrayList<>();

    public HoltWintersSmoothing(double alpha, double beta, double gamma,
                                 int seasonLength, double sensitivity, int minDataPoints) {
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
        this.seasonLength = seasonLength;
        this.sensitivity = sensitivity;
        this.minDataPoints = minDataPoints;
        this.seasonal = new double[seasonLength];
    }

    public HoltWintersSmoothing(int seasonLength, double sensitivity, int minDataPoints) {
        this(0.2, 0.1, 0.3, seasonLength, sensitivity, minDataPoints);
    }

    @Override
    public String name() {
        return "holt-winters";
    }

    @Override
    public ThresholdResult compute(List<DataPoint> dataPoints, double sensitivity) {
        if (dataPoints.size() < seasonLength * 2) {
            double mean = dataPoints.stream().mapToDouble(DataPoint::value).average().orElse(0.0);
            double stddev = Math.sqrt(dataPoints.stream()
                    .mapToDouble(dp -> Math.pow(dp.value() - mean, 2))
                    .average().orElse(0.0));
            return new ThresholdResult(mean + sensitivity * stddev, mean - sensitivity * stddev, mean, name());
        }

        List<Double> values = dataPoints.stream().map(DataPoint::value).toList();
        boolean allPositive = values.stream().allMatch(v -> v > 0);
        boolean multiplicative = allPositive;

        double initLevel = values.subList(0, seasonLength).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
        double initTrend = 0.0;
        if (values.size() >= seasonLength * 2) {
            double firstSeasonAvg = values.subList(0, seasonLength).stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0.0);
            double secondSeasonAvg = values.subList(seasonLength, seasonLength * 2).stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0.0);
            initTrend = (secondSeasonAvg - firstSeasonAvg) / seasonLength;
        }

        double[] s = new double[seasonLength];
        for (int i = 0; i < seasonLength; i++) {
            if (multiplicative && initLevel != 0) {
                s[i] = values.get(i) / initLevel;
            } else {
                s[i] = values.get(i) - initLevel;
            }
        }

        double l = initLevel;
        double t = initTrend;
        double resMu = 0.0;
        double resSigma = 0.0;
        boolean resInit = false;

        for (int i = seasonLength; i < values.size(); i++) {
            double x = values.get(i);
            int si = i % seasonLength;
            double prevL = l;

            double forecast;
            if (multiplicative) {
                l = alpha * x / s[si] + (1 - alpha) * (l + t);
                t = beta * (l - prevL) + (1 - beta) * t;
                s[si] = gamma * x / l + (1 - gamma) * s[si];
                forecast = (l + t) * s[si];
            } else {
                l = alpha * (x - s[si]) + (1 - alpha) * (l + t);
                t = beta * (l - prevL) + (1 - beta) * t;
                s[si] = gamma * (x - l) + (1 - gamma) * s[si];
                forecast = l + t + s[si];
            }

            double residual = x - forecast;
            if (!resInit) {
                resMu = residual;
                resSigma = Math.abs(residual);
                resInit = true;
            } else {
                resMu = RESIDUAL_ALPHA * residual + (1 - RESIDUAL_ALPHA) * resMu;
                resSigma = RESIDUAL_ALPHA * Math.abs(residual - resMu) + (1 - RESIDUAL_ALPHA) * resSigma;
            }
        }

        int nextSi = values.size() % seasonLength;
        double forecast;
        if (multiplicative) {
            forecast = (l + t) * s[nextSi];
        } else {
            forecast = l + t + s[nextSi];
        }

        double upper = forecast + sensitivity * resSigma;
        double lower = forecast - sensitivity * resSigma;
        return new ThresholdResult(upper, lower, forecast, name());
    }

    @Override
    public void update(double value, long timestampEpochSeconds) {
        count++;

        if (!initialized) {
            initBuffer.add(value);
            if (initBuffer.size() >= seasonLength * 2) {
                initialize();
            }
            return;
        }

        double prevLevel = level;
        int si = seasonIndex % seasonLength;

        if (useMultiplicative) {
            double sVal = seasonal[si] != 0 ? seasonal[si] : 1.0;
            level = alpha * value / sVal + (1 - alpha) * (level + trend);
            trend = beta * (level - prevLevel) + (1 - beta) * trend;
            seasonal[si] = level != 0 ? gamma * value / level + (1 - gamma) * seasonal[si] : seasonal[si];
        } else {
            level = alpha * (value - seasonal[si]) + (1 - alpha) * (level + trend);
            trend = beta * (level - prevLevel) + (1 - beta) * trend;
            seasonal[si] = gamma * (value - level) + (1 - gamma) * seasonal[si];
        }

        double forecast;
        if (useMultiplicative) {
            forecast = (level + trend) * seasonal[si];
        } else {
            forecast = level + trend + seasonal[si];
        }

        double residual = value - forecast;
        residualMu = RESIDUAL_ALPHA * residual + (1 - RESIDUAL_ALPHA) * residualMu;
        residualSigma = RESIDUAL_ALPHA * Math.abs(residual - residualMu) + (1 - RESIDUAL_ALPHA) * residualSigma;

        seasonIndex = (seasonIndex + 1) % seasonLength;
    }

    private void initialize() {
        useMultiplicative = initBuffer.stream().allMatch(v -> v > 0);

        level = initBuffer.subList(0, seasonLength).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);

        double firstAvg = initBuffer.subList(0, seasonLength).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
        double secondAvg = initBuffer.subList(seasonLength, seasonLength * 2).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
        trend = (secondAvg - firstAvg) / seasonLength;

        for (int i = 0; i < seasonLength; i++) {
            if (useMultiplicative && level != 0) {
                seasonal[i] = initBuffer.get(i) / level;
            } else {
                seasonal[i] = initBuffer.get(i) - level;
            }
        }

        seasonIndex = 0;
        initialized = true;
        for (int i = seasonLength; i < initBuffer.size(); i++) {
            double val = initBuffer.get(i);
            double prevLevel = level;
            int si = seasonIndex % seasonLength;

            if (useMultiplicative) {
                double sVal = seasonal[si] != 0 ? seasonal[si] : 1.0;
                level = alpha * val / sVal + (1 - alpha) * (level + trend);
                trend = beta * (level - prevLevel) + (1 - beta) * trend;
                seasonal[si] = level != 0 ? gamma * val / level + (1 - gamma) * seasonal[si] : seasonal[si];
            } else {
                level = alpha * (val - seasonal[si]) + (1 - alpha) * (level + trend);
                trend = beta * (level - prevLevel) + (1 - beta) * trend;
                seasonal[si] = gamma * (val - level) + (1 - gamma) * seasonal[si];
            }

            seasonIndex = (seasonIndex + 1) % seasonLength;
        }

        initBuffer.clear();
    }

    @Override
    public ThresholdResult currentThresholds() {
        if (!initialized) {
            return new ThresholdResult(Double.MAX_VALUE, -Double.MAX_VALUE, 0.0, name());
        }

        int si = seasonIndex % seasonLength;
        double forecast;
        if (useMultiplicative) {
            forecast = (level + trend) * seasonal[si];
        } else {
            forecast = level + trend + seasonal[si];
        }

        double upper = forecast + sensitivity * residualSigma;
        double lower = forecast - sensitivity * residualSigma;
        return new ThresholdResult(upper, lower, forecast, name());
    }

    @Override
    public boolean isReady() {
        return initialized && count >= minDataPoints;
    }

    public long getCount() {
        return count;
    }

    public double getLevel() {
        return level;
    }

    @Override
    public boolean supportsSerialize() {
        return true;
    }

    @Override
    public String serializeState() {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("alpha", alpha);
            m.put("beta", beta);
            m.put("gamma", gamma);
            m.put("seasonLength", seasonLength);
            m.put("sensitivity", sensitivity);
            m.put("minDataPoints", minDataPoints);
            m.put("level", level);
            m.put("trend", trend);
            m.put("seasonal", seasonal);
            m.put("seasonIndex", seasonIndex);
            m.put("count", count);
            m.put("initialized", initialized);
            m.put("useMultiplicative", useMultiplicative);
            m.put("residualMu", residualMu);
            m.put("residualSigma", residualSigma);
            return new ObjectMapper().writeValueAsString(m);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    public static HoltWintersSmoothing restoreFrom(String json) {
        try {
            Map<String, Object> m = new ObjectMapper().readValue(json, Map.class);
            double alpha = ((Number) m.get("alpha")).doubleValue();
            double beta = ((Number) m.get("beta")).doubleValue();
            double gamma = ((Number) m.get("gamma")).doubleValue();
            int seasonLength = ((Number) m.get("seasonLength")).intValue();
            double sensitivity = ((Number) m.get("sensitivity")).doubleValue();
            int minDataPoints = ((Number) m.get("minDataPoints")).intValue();

            HoltWintersSmoothing instance = new HoltWintersSmoothing(
                    alpha, beta, gamma, seasonLength, sensitivity, minDataPoints);
            instance.level = ((Number) m.get("level")).doubleValue();
            instance.trend = ((Number) m.get("trend")).doubleValue();
            instance.seasonIndex = ((Number) m.get("seasonIndex")).intValue();
            instance.count = ((Number) m.get("count")).longValue();
            instance.initialized = (Boolean) m.get("initialized");
            instance.useMultiplicative = (Boolean) m.get("useMultiplicative");
            instance.residualMu = ((Number) m.get("residualMu")).doubleValue();
            instance.residualSigma = ((Number) m.get("residualSigma")).doubleValue();

            List<Number> seasonalList = (List<Number>) m.get("seasonal");
            for (int i = 0; i < seasonLength && i < seasonalList.size(); i++) {
                instance.seasonal[i] = seasonalList.get(i).doubleValue();
            }

            return instance;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to restore HoltWintersSmoothing", e);
        }
    }
}
