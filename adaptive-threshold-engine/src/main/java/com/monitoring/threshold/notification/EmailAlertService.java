package com.monitoring.threshold.notification;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.monitoring.threshold.engine.AnomalyEvent;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@ConditionalOnProperty(name = "alerting.email.enabled", havingValue = "true")
public class EmailAlertService {

    private static final Logger log = LoggerFactory.getLogger(EmailAlertService.class);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private final JavaMailSender mailSender;
    private final AlertDeduplicator deduplicator;

    @Value("${alerting.email.from}")
    private String fromAddress;

    @Value("${alerting.email.recipients}")
    private String[] recipients;

    @Value("${alerting.email.subject-prefix:[Threshold Engine]}")
    private String subjectPrefix;

    public EmailAlertService(JavaMailSender mailSender, AlertDeduplicator deduplicator) {
        this.mailSender = mailSender;
        this.deduplicator = deduplicator;
        log.info("Email alerting enabled — recipients: {}", String.join(", ", recipients != null ? recipients : new String[]{}));
    }

    public boolean trySendAlert(AnomalyEvent event) {
        if (!deduplicator.shouldSend(event)) {
            log.debug("Alert for '{}' suppressed by deduplicator", event.metricName());
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(recipients);
            helper.setSubject(buildSubject(event));
            helper.setText(buildBody(event), true);

            mailSender.send(message);
            log.info("Alert email sent for '{}' [{}]", event.metricName(), event.severity());
            return true;

        } catch (MessagingException | MailException e) {
            log.error("Failed to send alert email for '{}': {}", event.metricName(), e.getMessage());
            return false;
        }
    }

    private String buildSubject(AnomalyEvent event) {
        String icon = event.severity() == AnomalyEvent.Severity.CRITICAL ? "🔴" : "⚠️";
        return String.format("%s %s %s — %s breach on %s",
                subjectPrefix, icon, event.severity(),
                event.thresholdBreached(), event.metricName());
    }

    private String buildBody(AnomalyEvent event) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px;">
                  <h2 style="color: %s;">%s Anomaly: %s</h2>
                  <table style="border-collapse: collapse; width: 100%%;">
                    <tr><td style="padding: 6px 12px; font-weight: bold;">Metric</td>
                        <td style="padding: 6px 12px;">%s</td></tr>
                    <tr style="background: #f5f5f5;">
                        <td style="padding: 6px 12px; font-weight: bold;">Threshold Breached</td>
                        <td style="padding: 6px 12px;">%s</td></tr>
                    <tr><td style="padding: 6px 12px; font-weight: bold;">Current Value</td>
                        <td style="padding: 6px 12px;">%.4f</td></tr>
                    <tr style="background: #f5f5f5;">
                        <td style="padding: 6px 12px; font-weight: bold;">Deviation Score</td>
                        <td style="padding: 6px 12px;">%.2f×</td></tr>
                    <tr><td style="padding: 6px 12px; font-weight: bold;">Algorithm</td>
                        <td style="padding: 6px 12px;">%s</td></tr>
                    <tr style="background: #f5f5f5;">
                        <td style="padding: 6px 12px; font-weight: bold;">Detected At</td>
                        <td style="padding: 6px 12px;">%s</td></tr>
                  </table>
                  <p style="color: #888; font-size: 12px; margin-top: 16px;">
                    Adaptive Threshold Engine &mdash; auto-generated alert
                  </p>
                </div>
                """.formatted(
                event.severity() == AnomalyEvent.Severity.CRITICAL ? "#d32f2f" : "#ed6c02",
                event.severity(),
                event.metricName(),
                event.metricName(),
                event.thresholdBreached(),
                event.value(),
                event.deviationScore(),
                event.algorithmUsed(),
                TIME_FMT.format(event.timestamp())
        );
    }
}
