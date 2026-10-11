package com.cloudpulse.service;

import com.cloudpulse.domain.Incident;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Sends e-mail alerts when incidents open or resolve. Disabled unless configured. */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final List<String> recipients;
    private final String from;

    public AlertService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${cloudpulse.alerts.enabled:false}") boolean enabled,
                        @Value("${cloudpulse.alerts.recipients:}") String recipients,
                        @Value("${cloudpulse.alerts.from:cloudpulse@localhost}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.recipients = Arrays.stream(recipients.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.from = from;
    }

    public void incidentOpened(String serviceName, String url, Incident incident) {
        send("[CloudPulse] DOWN: " + serviceName,
                "Service: " + serviceName + "\nURL: " + url
                        + "\nDetected at: " + incident.getStartedAt()
                        + "\nReason: " + incident.getReason() + "\n");
    }

    public void incidentResolved(String serviceName, String url, Incident incident) {
        long seconds = Duration.between(incident.getStartedAt(), incident.getResolvedAt()).toSeconds();
        send("[CloudPulse] RECOVERED: " + serviceName,
                "Service: " + serviceName + "\nURL: " + url
                        + "\nRecovered at: " + incident.getResolvedAt()
                        + "\nTotal downtime: " + seconds + " seconds\n");
    }

    private void send(String subject, String body) {
        if (!enabled || recipients.isEmpty()) {
            log.info("Alert (email disabled): {}", subject);
            return;
        }
        try {
            JavaMailSender sender = mailSender.getIfAvailable();
            if (sender == null) {
                log.warn("Alerts enabled but no mail server is configured (spring.mail.host)");
                return;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipients.toArray(new String[0]));
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (Exception e) {
            // An alerting failure must never break monitoring.
            log.warn("Could not send alert '{}': {}", subject, e.getMessage());
        }
    }
}
