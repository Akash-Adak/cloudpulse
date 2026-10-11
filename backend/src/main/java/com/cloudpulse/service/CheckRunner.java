package com.cloudpulse.service;

import com.cloudpulse.domain.MonitoredService;
import com.cloudpulse.net.ProbeResult;
import com.cloudpulse.net.SafeHttpProbe;
import com.cloudpulse.repo.MonitoredServiceRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Runs one probe for one service: probe, persist, then alert (outside the DB transaction). */
@Service
public class CheckRunner {

    private final MonitoredServiceRepository services;
    private final SafeHttpProbe probe;
    private final CheckRecorder recorder;
    private final AlertService alerts;
    private final MetricsCollector metrics;

    public CheckRunner(MonitoredServiceRepository services, SafeHttpProbe probe,
                       CheckRecorder recorder, AlertService alerts, MetricsCollector metrics) {
        this.services = services;
        this.probe = probe;
        this.recorder = recorder;
        this.alerts = alerts;
        this.metrics = metrics;
    }

    /** @return the probe result, or empty if the service no longer exists */
    public Optional<ProbeResult> runCheck(Long serviceId) {
        Optional<MonitoredService> found = services.findById(serviceId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        String url = found.get().getUrl();
        int timeoutMs = found.get().getTimeoutMs();

        ProbeResult result = probe.probe(url, timeoutMs);

        recorder.record(serviceId, result).ifPresent(outcome -> {
            if (outcome.opened() != null) {
                alerts.incidentOpened(outcome.serviceName(), outcome.url(), outcome.opened());
            }
            if (outcome.resolved() != null) {
                alerts.incidentResolved(outcome.serviceName(), outcome.url(), outcome.resolved());
            }
        });
        if (result.success() && found.get().isActuatorMetricsEnabled()) {
            metrics.collect(found.get());
        }
        return Optional.of(result);
    }
}
