package com.monitoring.threshold.prometheus;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.monitoring.threshold.config.ThresholdEngineProperties;
import com.monitoring.threshold.prometheus.dto.PrometheusResponse;
import com.monitoring.threshold.prometheus.dto.PrometheusResponse.PrometheusResult;

@Service
public class PrometheusQueryService {

    private static final Logger log = LoggerFactory.getLogger(PrometheusQueryService.class);

    private final WebClient prometheusWebClient;
    private final String prometheusBaseUrl;

    public PrometheusQueryService(WebClient prometheusWebClient, ThresholdEngineProperties properties) {
        this.prometheusWebClient = prometheusWebClient;
        this.prometheusBaseUrl = properties.getPrometheus().getUrl();
    }

    public List<PrometheusResult> queryRange(String query, Instant start, Instant end, String step) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = URI.create(prometheusBaseUrl + "/api/v1/query_range?query=" + encodedQuery
                    + "&start=" + start.getEpochSecond()
                    + "&end=" + end.getEpochSecond()
                    + "&step=" + step);

            PrometheusResponse response = prometheusWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(PrometheusResponse.class)
                    .block();

            if (response != null && "success".equals(response.status()) && response.data() != null) {
                return response.data().result();
            }
            log.warn("Prometheus query_range returned non-success for query='{}': {}",
                    query, response != null ? response.status() : "null");
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to execute query_range for query='{}': {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<PrometheusResult> queryInstant(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = URI.create(prometheusBaseUrl + "/api/v1/query?query=" + encodedQuery);

            PrometheusResponse response = prometheusWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(PrometheusResponse.class)
                    .block();

            if (response != null && "success".equals(response.status()) && response.data() != null) {
                return response.data().result();
            }
            log.warn("Prometheus instant query returned non-success for query='{}': {}",
                    query, response != null ? response.status() : "null");
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to execute instant query for query='{}': {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }
}
