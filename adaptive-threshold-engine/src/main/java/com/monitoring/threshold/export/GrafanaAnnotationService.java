package com.monitoring.threshold.export;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.monitoring.threshold.engine.AnomalyEvent;

@Service
public class GrafanaAnnotationService {

    private static final Logger log = LoggerFactory.getLogger(GrafanaAnnotationService.class);

    private final WebClient grafanaClient;

    public GrafanaAnnotationService(
            @Value("${threshold-engine.grafana.url:http://grafana:3000}") String grafanaUrl,
            @Value("${threshold-engine.grafana.username:admin}") String username,
            @Value("${threshold-engine.grafana.password:admin}") String password) {

        this.grafanaClient = WebClient.builder()
                .baseUrl(grafanaUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password))
                .build();
    }

    public void pushAnomaly(AnomalyEvent event) {
        try {
            String text = String.format(
                    "Anomaly detected on metric '%s'\n" +
                    "Value: %.4f\n" +
                    "Threshold breached: %s\n" +
                    "Algorithm: %s\n" +
                    "Deviation score: %.2f\n" +
                    "Severity: %s",
                    event.metricName(),
                    event.value(),
                    event.thresholdBreached().name(),
                    event.algorithmUsed(),
                    event.deviationScore(),
                    event.severity().name()
            );

            List<String> tags = List.of(
                    "anomaly",
                    event.metricName(),
                    event.severity().name().toLowerCase()
            );

            Map<String, Object> body = Map.of(
                    "time", event.timestamp().toEpochMilli(),
                    "tags", tags,
                    "text", text
            );

            grafanaClient.post()
                    .uri("/api/annotations")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                            response -> log.debug("Grafana annotation created for metric '{}': {}",
                                    event.metricName(), response),
                            error -> log.warn("Failed to create Grafana annotation for metric '{}': {}",
                                    event.metricName(), error.getMessage())
                    );

        } catch (Exception e) {
            log.warn("Error pushing anomaly annotation to Grafana: {}", e.getMessage());
        }
    }
}
