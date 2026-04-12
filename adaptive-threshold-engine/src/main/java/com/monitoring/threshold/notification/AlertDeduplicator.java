package com.monitoring.threshold.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.monitoring.threshold.engine.AnomalyEvent;

@Component
public class AlertDeduplicator {

    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, DedupEntry> entries = new ConcurrentHashMap<>();

    public boolean shouldSend(AnomalyEvent event) {
        String key = event.metricName();
        DedupEntry existing = entries.get(key);

        if (existing == null) {
            entries.put(key, new DedupEntry(Instant.now(), event.severity()));
            return true;
        }

        if (existing.severity == AnomalyEvent.Severity.WARNING
                && event.severity() == AnomalyEvent.Severity.CRITICAL) {
            entries.put(key, new DedupEntry(Instant.now(), event.severity()));
            return true;
        }

        if (existing.lastSent.plus(DEDUP_WINDOW).isBefore(Instant.now())) {
            entries.put(key, new DedupEntry(Instant.now(), event.severity()));
            return true;
        }

        return false;
    }

    public void reset(String metricName) {
        entries.remove(metricName);
    }

    private record DedupEntry(Instant lastSent, AnomalyEvent.Severity severity) {
    }
}
