package com.monitoring.threshold.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AlgorithmStateRepository {

    private final JdbcTemplate jdbc;

    public AlgorithmStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(String metricKey, String algorithmName, String stateJson) {
        jdbc.update("""
                INSERT INTO algorithm_state_store (metric_key, algorithm_name, state_json, saved_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (metric_key)
                DO UPDATE SET algorithm_name = EXCLUDED.algorithm_name,
                              state_json = EXCLUDED.state_json,
                              saved_at = EXCLUDED.saved_at
                """, metricKey, algorithmName, stateJson,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    public Optional<AlgorithmStateRow> findByKey(String metricKey) {
        var rows = jdbc.query(
                "SELECT metric_key, algorithm_name, state_json, saved_at FROM algorithm_state_store WHERE metric_key = ?",
                (rs, rowNum) -> new AlgorithmStateRow(
                        rs.getString("metric_key"),
                        rs.getString("algorithm_name"),
                        rs.getString("state_json"),
                        rs.getObject("saved_at", OffsetDateTime.class).toInstant()),
                metricKey);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public record AlgorithmStateRow(String metricKey, String algorithmName,
                                     String stateJson, Instant savedAt) {
    }
}
