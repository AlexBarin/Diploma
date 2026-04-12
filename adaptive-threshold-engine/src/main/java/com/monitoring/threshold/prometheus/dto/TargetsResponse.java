package com.monitoring.threshold.prometheus.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TargetsResponse(String status, TargetsData data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TargetsData(List<ActiveTarget> activeTargets) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActiveTarget(
            Map<String, String> discoveredLabels,
            Map<String, String> labels,
            String scrapePool,
            String scrapeUrl,
            String globalUrl,
            String lastError,
            String lastScrape,
            String lastScrapeDuration,
            String health
    ) {
        public String job() {
            return labels != null ? labels.getOrDefault("job", "") : "";
        }
    }
}
