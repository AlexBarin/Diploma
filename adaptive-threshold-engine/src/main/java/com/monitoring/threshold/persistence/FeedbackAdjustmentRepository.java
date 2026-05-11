package com.monitoring.threshold.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackAdjustmentRepository {

    private final JdbcTemplate jdbc;

    public FeedbackAdjustmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(String metricName, double oldSensitivity, double newSensitivity,
                     double fdr, int tp, int fp, int ex, String direction) {
        jdbc.update("""
                INSERT INTO feedback_adjustments
                (metric_name, old_sensitivity, new_sensitivity, fdr_at_time, tp_count, fp_count, ex_count, direction)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                metricName, oldSensitivity, newSensitivity, fdr, tp, fp, ex, direction);
    }

    public List<AdjustmentRow> findAll() {
        return jdbc.query(
                "SELECT * FROM feedback_adjustments ORDER BY adjusted_at DESC LIMIT 100",
                (rs, rowNum) -> new AdjustmentRow(
                        rs.getLong("id"),
                        rs.getString("metric_name"),
                        rs.getDouble("old_sensitivity"),
                        rs.getDouble("new_sensitivity"),
                        rs.getDouble("fdr_at_time"),
                        rs.getInt("tp_count"),
                        rs.getInt("fp_count"),
                        rs.getInt("ex_count"),
                        rs.getString("direction"),
                        rs.getObject("adjusted_at", OffsetDateTime.class).toInstant()));
    }

    public record AdjustmentRow(long id, String metricName, double oldSensitivity,
                                 double newSensitivity, double fdr,
                                 int tp, int fp, int ex, String direction,
                                 java.time.Instant adjustedAt) {
    }
}
