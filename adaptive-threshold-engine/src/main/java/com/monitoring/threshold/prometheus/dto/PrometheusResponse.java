package com.monitoring.threshold.prometheus.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrometheusResponse(String status, PrometheusData data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrometheusData(String resultType, List<PrometheusResult> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrometheusResult(Map<String, String> metric, List<List<Object>> values) {
    }
}
