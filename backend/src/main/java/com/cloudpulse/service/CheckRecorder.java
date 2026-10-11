package com.cloudpulse.service;

import com.cloudpulse.domain.HealthCheck;
import com.cloudpulse.domain.Incident;
import com.cloudpulse.domain.MonitoredService;
import com.cloudpulse.domain.ServiceStatus;
import com.cloudpulse.net.ProbeResult;
import com.cloudpulse.repo.HealthCheckRepository;
import com.cloudpulse.repo.IncidentRepository;
import com.cloudpulse.repo.MonitoredServiceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a probe result and runs the up/down state machine:
 *
 * <pre>
 *   UNKNOWN --success--> UP
 *   UP/UNKNOWN --N consecutive failures--> DOWN   (opens exactly one incident)
 *   DOWN --M consecutive successes--> UP          (resolves the open incident)
 * </pre>
 * N is the per-service failure threshold, M is {@code cloudpulse.recovery-threshold}.
 * A single transient failure therefore never opens an incident.
 */
@Service
public class CheckRecorder {

    private static final Logger log = LoggerFactory.getLogger(CheckRecorder.class);

    /** What changed as a result of recording a check. Used to trigger alerts after commit. */
    public record Outcome(String serviceName, String url, Incident opened, Incident resolved) {
    }

    private final MonitoredServiceRepository services;
    private final HealthCheckRepository checks;
    private final IncidentRepository incidents;
    private final Clock clock;
    private final int recoveryThreshold;

    public CheckRecorder(MonitoredServiceRepository services, HealthCheckRepository checks,
                         IncidentRepository incidents, Clock clock,
                         @Value("${cloudpulse.recovery-threshold:1}") int recoveryThreshold) {
        this.services = services;
        this.checks = checks;
        this.incidents = incidents;
        this.clock = clock;
        this.recoveryThreshold = Math.max(1, recoveryThreshold);
    }

    @Transactional
    public Optional<Outcome> record(Long serviceId, ProbeResult probe) {
        Optional<MonitoredService> found = services.findById(serviceId);
        if (found.isEmpty()) {
            return Optional.empty(); // service was deleted while the probe was running
        }
        MonitoredService service = found.get();
        Instant now = clock.instant();

        HealthCheck check = new HealthCheck();
        check.setServiceId(serviceId);
        check.setCheckedAt(now);
        check.setSuccess(probe.success());
        check.setStatusCode(probe.statusCode());
        check.setResponseTimeMs(probe.responseTimeMs());
        check.setErrorMessage(probe.error());
        checks.save(check);

        service.setLastCheckedAt(now);
        service.setLastStatusCode(probe.statusCode());
        service.setLastResponseTimeMs(probe.responseTimeMs());
        service.setUpdatedAt(now);

        Incident opened = null;
        Incident resolved = null;

        if (probe.success()) {
            service.setConsecutiveFailures(0);
            service.setConsecutiveSuccesses(service.getConsecutiveSuccesses() + 1);
            if (service.getStatus() == ServiceStatus.DOWN) {
                if (service.getConsecutiveSuccesses() >= recoveryThreshold) {
                    service.setStatus(ServiceStatus.UP);
                    resolved = resolveOpenIncident(serviceId, now);
                    log.info("Service '{}' recovered", service.getName());
                }
            } else {
                service.setStatus(ServiceStatus.UP);
            }
        } else {
            service.setConsecutiveSuccesses(0);
            service.setConsecutiveFailures(service.getConsecutiveFailures() + 1);
            boolean thresholdReached = service.getConsecutiveFailures() >= service.getFailureThreshold();
            if (thresholdReached && service.getStatus() != ServiceStatus.DOWN) {
                service.setStatus(ServiceStatus.DOWN);
                if (incidents.findFirstByServiceIdAndResolvedAtIsNull(serviceId).isEmpty()) {
                    Incident incident = new Incident();
                    incident.setServiceId(serviceId);
                    incident.setStartedAt(now);
                    incident.setReason(probe.error());
                    opened = incidents.save(incident);
                    log.warn("Incident opened for '{}': {}", service.getName(), probe.error());
                }
            }
        }

        services.save(service);
        return Optional.of(new Outcome(service.getName(), service.getUrl(), opened, resolved));
    }

    private Incident resolveOpenIncident(Long serviceId, Instant now) {
        Optional<Incident> open = incidents.findFirstByServiceIdAndResolvedAtIsNull(serviceId);
        if (open.isEmpty()) {
            return null;
        }
        Incident incident = open.get();
        incident.setResolvedAt(now);
        return incidents.save(incident);
    }
}
