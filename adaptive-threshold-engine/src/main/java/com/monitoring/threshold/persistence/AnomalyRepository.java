package com.monitoring.threshold.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.monitoring.threshold.engine.AnomalyEvent;
import com.monitoring.threshold.engine.MetricState;

@Repository
public class AnomalyRepository {

    private final JdbcTemplate jdbc;

    public AnomalyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAnomaly(AnomalyEvent event, MetricState state) {
        var thresholds = state.getCurrentThresholdResult();
        jdbc.update("""
                INSERT INTO anomaly_events
                (metric_name, job_name, event_timestamp, value, threshold_upper, threshold_lower,
                 midline, threshold_breached, severity, algorithm_used, deviation_score)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.metricName(),
                state.getJobName(),
                OffsetDateTime.ofInstant(event.timestamp(), ZoneOffset.UTC),
                event.value(),
                thresholds != null ? thresholds.upper() : null,
                thresholds != null ? thresholds.lower() : null,
                thresholds != null ? thresholds.midline() : null,
                event.thresholdBreached().name(),
                event.severity().name(),
                event.algorithmUsed(),
                event.deviationScore());
    }

    public AnomalyRow findById(long id) {
        var rows = jdbc.query(
                "SELECT * FROM anomaly_events WHERE id = ?",
                (rs, rowNum) -> mapRow(rs), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<AnomalyRow> findUnlabeled(int limit, int offset) {
        return jdbc.query(
                "SELECT * FROM anomaly_events WHERE operator_label IS NULL ORDER BY event_timestamp DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapRow(rs), limit, offset);
    }

    public List<AnomalyRow> findLabeledByMetricSince(String metricName, Instant since) {
        return jdbc.query(
                "SELECT * FROM anomaly_events WHERE metric_name = ? AND operator_label IS NOT NULL AND labeled_at > ? ORDER BY event_timestamp DESC",
                (rs, rowNum) -> mapRow(rs),
                metricName, OffsetDateTime.ofInstant(since, ZoneOffset.UTC));
    }

    public List<String> findDistinctLabeledMetrics(Instant since) {
        return jdbc.queryForList(
                "SELECT DISTINCT metric_name FROM anomaly_events WHERE operator_label IS NOT NULL AND labeled_at > ?",
                String.class,
                OffsetDateTime.ofInstant(since, ZoneOffset.UTC));
    }

    public void setLabel(long id, String label) {
        jdbc.update(
                "UPDATE anomaly_events SET operator_label = ?, labeled_at = ? WHERE id = ?",
                label, OffsetDateTime.now(ZoneOffset.UTC), id);
    }

    public List<AnomalyRow> findRecent(String metricName, String severity, Instant since, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM anomaly_events WHERE event_timestamp > ?");
        var params = new java.util.ArrayList<Object>();
        params.add(OffsetDateTime.ofInstant(since, ZoneOffset.UTC));

        if (metricName != null) {
            sql.append(" AND metric_name = ?");
            params.add(metricName);
        }
        if (severity != null) {
            sql.append(" AND severity = ?");
            params.add(severity);
        }
        sql.append(" ORDER BY event_timestamp DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), (rs, rowNum) -> mapRow(rs), params.toArray());
    }

    public int deleteOlderThan(Instant cutoff) {
        return jdbc.update("DELETE FROM anomaly_events WHERE event_timestamp < ?",
                OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC));
    }

    private AnomalyRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AnomalyRow(
                rs.getLong("id"),
                rs.getString("metric_name"),
                rs.getString("job_name"),
                rs.getObject("event_timestamp", OffsetDateTime.class).toInstant(),
                rs.getDouble("value"),
                rs.getDouble("threshold_upper"),
                rs.getDouble("threshold_lower"),
                rs.getDouble("midline"),
                rs.getString("threshold_breached"),
                rs.getString("severity"),
                rs.getString("algorithm_used"),
                rs.getDouble("deviation_score"),
                rs.getString("operator_label"),
                rs.getObject("labeled_at", OffsetDateTime.class) != null
                        ? rs.getObject("labeled_at", OffsetDateTime.class).toInstant() : null);
    }

    public record AnomalyRow(long id, String metricName, String jobName, Instant timestamp,
                               double value, double thresholdUpper, double thresholdLower,
                               double midline, String thresholdBreached, String severity,
                               String algorithmUsed, double deviationScore,
                               String operatorLabel, Instant labeledAt) {
    }
}
