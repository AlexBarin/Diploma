package com.monitoring.threshold.prometheus;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.monitoring.threshold.config.ThresholdEngineProperties;
import com.monitoring.threshold.prometheus.dto.TargetsResponse;
import com.monitoring.threshold.prometheus.dto.TargetsResponse.ActiveTarget;

@Service
public class PrometheusMetricDiscovery {

    private static final Logger log = LoggerFactory.getLogger(PrometheusMetricDiscovery.class);

    private final WebClient prometheusWebClient;
    private final ThresholdEngineProperties properties;
    private final String prometheusBaseUrl;

    public PrometheusMetricDiscovery(WebClient prometheusWebClient, ThresholdEngineProperties properties) {
        this.prometheusWebClient = prometheusWebClient;
        this.properties = properties;
        this.prometheusBaseUrl = properties.getPrometheus().getUrl();
    }

    public Map<String, List<String>> discoverMetrics() {
        var discovery = properties.getPrometheus().getDiscovery();

        if (discovery.isExplicitMode()) {
            return discoverExplicit(discovery);
        }
        return discoverAuto(discovery);
    }

    private Map<String, List<String>> discoverExplicit(
            com.monitoring.threshold.config.ThresholdEngineProperties.Discovery discovery) {
        Map<String, List<String>> result = new HashMap<>();
        Map<String, List<String>> includeMetrics = discovery.getIncludeMetrics();

        for (Map.Entry<String, List<String>> entry : includeMetrics.entrySet()) {
            String job = entry.getKey();
            List<String> metrics = entry.getValue();
            if (metrics != null && !metrics.isEmpty()) {
                result.put(job, new ArrayList<>(metrics));
            }
        }

        int totalMetrics = result.values().stream().mapToInt(List::size).sum();
        log.info("Explicit mode: {} jobs with {} curated metrics", result.size(), totalMetrics);
        return result;
    }

    private Map<String, List<String>> discoverAuto(
            com.monitoring.threshold.config.ThresholdEngineProperties.Discovery discovery) {
        List<String> excludeJobs = discovery.getExcludeJobs();
        List<String> excludePrefixes = discovery.getExcludeMetricPrefixes();

        Set<String> activeJobs = fetchActiveJobs().stream()
                .filter(job -> !excludeJobs.contains(job))
                .collect(Collectors.toSet());

        Map<String, List<String>> result = new HashMap<>();

        for (String job : activeJobs) {
            List<String> jobMetrics = fetchMetricNamesForJob(job);
            List<String> filtered = jobMetrics.stream()
                    .filter(metric -> excludePrefixes.stream().noneMatch(metric::startsWith))
                    .toList();
            if (!filtered.isEmpty()) {
                result.put(job, new ArrayList<>(filtered));
            }
        }

        int totalMetrics = result.values().stream().mapToInt(List::size).sum();
        log.info("Auto mode: {} jobs with {} total metrics (after filtering)", result.size(), totalMetrics);
        return result;
    }

    private List<String> fetchMetricNamesForJob(String job) {
        try {
            String matchParam = URLEncoder.encode("{job=\"" + job + "\"}", StandardCharsets.UTF_8);
            URI uri = URI.create(prometheusBaseUrl + "/api/v1/label/__name__/values?match%5B%5D=" + matchParam);

            Map<String, Object> response = prometheusWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null && "success".equals(response.get("status"))) {
                Object data = response.get("data");
                if (data instanceof List<?> names) {
                    return names.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .toList();
                }
            }
            return List.of();
        } catch (Exception e) {
            log.error("Error fetching metric names for job '{}': {}", job, e.getMessage());
            return List.of();
        }
    }

    private List<String> fetchActiveJobs() {
        try {
            TargetsResponse response = prometheusWebClient.get()
                    .uri("/api/v1/targets")
                    .retrieve()
                    .bodyToMono(TargetsResponse.class)
                    .block();

            if (response != null && "success".equals(response.status()) && response.data() != null) {
                return response.data().activeTargets().stream()
                        .map(ActiveTarget::job)
                        .filter(job -> !job.isEmpty())
                        .distinct()
                        .toList();
            }
            log.warn("Failed to fetch targets from Prometheus");
            return List.of();
        } catch (Exception e) {
            log.error("Error fetching targets: {}", e.getMessage());
            return List.of();
        }
    }
}
