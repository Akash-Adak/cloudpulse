package com.cloudpulse.service;

import com.cloudpulse.domain.HealthCheck;
import com.cloudpulse.domain.Incident;
import com.cloudpulse.domain.MonitoredService;
import com.cloudpulse.domain.ServiceStatus;
import com.cloudpulse.net.SsrfGuard;
import com.cloudpulse.net.UnsafeTargetException;
import com.cloudpulse.repo.HealthCheckRepository;
import com.cloudpulse.repo.IncidentRepository;
import com.cloudpulse.repo.MonitoredServiceRepository;
import com.cloudpulse.web.Dto.RecentCheck;
import com.cloudpulse.web.Dto.ServiceRequest;
import com.cloudpulse.web.Dto.ServiceResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD for monitored services. Every URL goes through the SSRF guard before it is stored. */
@Service
public class ServiceCatalog {

    private static final int DEFAULT_INTERVAL_SECONDS = 60;
    private static final int DEFAULT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final int RECENT_CHECKS = 40;

    private final MonitoredServiceRepository services;
    private final HealthCheckRepository checks;
    private final IncidentRepository incidents;
    private final SsrfGuard guard;
    private final Clock clock;

    public ServiceCatalog(MonitoredServiceRepository services, HealthCheckRepository checks,
                          IncidentRepository incidents, SsrfGuard guard, Clock clock) {
        this.services = services;
        this.checks = checks;
        this.incidents = incidents;
        this.guard = guard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> list() {
        return services.findAll(Sort.by("name").ascending()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ServiceResponse create(ServiceRequest request) throws UnsafeTargetException {
        String url = guard.validate(request.url()).toString();
        String name = request.name().trim();
        if (services.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A service named '" + name + "' already exists");
        }
        MonitoredService service = new MonitoredService();
        apply(service, request, name, url);
        Instant now = clock.instant();
        service.setCreatedAt(now);
        service.setUpdatedAt(now);
        return toResponse(services.save(service));
    }

    @Transactional
    public ServiceResponse update(Long id, ServiceRequest request) throws UnsafeTargetException {
        MonitoredService service = find(id);
        String url = guard.validate(request.url()).toString();
        String name = request.name().trim();
        if (services.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A service named '" + name + "' already exists");
        }
        boolean urlChanged = !url.equals(service.getUrl());
        boolean wasEnabled = service.isEnabled();
        apply(service, request, name, url);
        if (urlChanged || (wasEnabled && !service.isEnabled())) {
            resetState(service);
        }
        service.setUpdatedAt(clock.instant());
        return toResponse(services.save(service));
    }

    @Transactional
    public ServiceResponse setEnabled(Long id, boolean enabled) {
        MonitoredService service = find(id);
        service.setEnabled(enabled);
        if (!enabled) {
            resetState(service);
        }
        service.setUpdatedAt(clock.instant());
        return toResponse(services.save(service));
    }

    @Transactional
    public void delete(Long id) {
        MonitoredService service = find(id);
        services.delete(service); // check history and incidents are removed by ON DELETE CASCADE
    }

    public MonitoredService find(Long id) {
        return services.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service " + id + " was not found"));
    }

    // ---- helpers -----------------------------------------------------------------------------

    private void apply(MonitoredService s, ServiceRequest r, String name, String url) {
        s.setName(name);
        s.setUrl(url);
        s.setProvider(r.provider());
        s.setCheckIntervalSeconds(r.checkIntervalSeconds() != null ? r.checkIntervalSeconds() : DEFAULT_INTERVAL_SECONDS);
        s.setTimeoutMs(r.timeoutMs() != null ? r.timeoutMs() : DEFAULT_TIMEOUT_MS);
        s.setFailureThreshold(r.failureThreshold() != null ? r.failureThreshold() : DEFAULT_FAILURE_THRESHOLD);
        s.setEnabled(r.enabled() == null || r.enabled());
        s.setActuatorMetricsEnabled(Boolean.TRUE.equals(r.actuatorMetricsEnabled()));
    }

    /** Forget live state (new target or paused monitoring) and close any open incident. */
    private void resetState(MonitoredService service) {
        service.setStatus(ServiceStatus.UNKNOWN);
        service.setConsecutiveFailures(0);
        service.setConsecutiveSuccesses(0);
        service.setLastCheckedAt(null);
        service.setLastStatusCode(null);
        service.setLastResponseTimeMs(null);
        if (service.getId() != null) {
            incidents.findFirstByServiceIdAndResolvedAtIsNull(service.getId()).ifPresent((Incident i) -> {
                i.setResolvedAt(clock.instant());
                incidents.save(i);
            });
        }
    }

    private ServiceResponse toResponse(MonitoredService s) {
        List<HealthCheck> latest = checks.findByServiceIdOrderByCheckedAtDesc(s.getId(), PageRequest.of(0, RECENT_CHECKS));
        List<RecentCheck> recent = new ArrayList<>(latest.size());
        for (HealthCheck h : latest) {
            recent.add(new RecentCheck(h.isSuccess(), h.getResponseTimeMs()));
        }
        Collections.reverse(recent); // oldest -> newest for the heartbeat strip
        return new ServiceResponse(s.getId(), s.getName(), s.getUrl(), s.getProvider(),
                s.getCheckIntervalSeconds(), s.getTimeoutMs(), s.getFailureThreshold(),
                s.isEnabled(), s.getStatus(), s.getConsecutiveFailures(),
                s.getLastCheckedAt(), s.getLastStatusCode(), s.getLastResponseTimeMs(),
                s.getCreatedAt(), s.isActuatorMetricsEnabled(), recent);
    }
}
