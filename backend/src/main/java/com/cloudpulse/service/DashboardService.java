package com.cloudpulse.service;

import com.cloudpulse.domain.HealthCheck;
import com.cloudpulse.domain.Incident;
import com.cloudpulse.domain.MonitoredService;
import com.cloudpulse.domain.MetricSample;
import com.cloudpulse.domain.ServiceStatus;
import com.cloudpulse.repo.HealthCheckRepository;
import com.cloudpulse.repo.IncidentRepository;
import com.cloudpulse.repo.MonitoredServiceRepository;
import com.cloudpulse.web.Dto.CheckResponse;
import com.cloudpulse.web.Dto.IncidentResponse;
import com.cloudpulse.web.Dto.LatencyPoint;
import com.cloudpulse.web.Dto.LatencyResponse;
import com.cloudpulse.web.Dto.Stats;
import com.cloudpulse.web.Dto.SummaryResponse;
import com.cloudpulse.web.Dto.MetricPoint;
import com.cloudpulse.web.Dto.MetricsResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only queries behind the dashboard. Everything is derived from stored checks. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int MAX_POINTS = 300;

    private final MonitoredServiceRepository services;
    private final HealthCheckRepository checks;
    private final IncidentRepository incidents;
    private final ServiceCatalog catalog;
    private final Clock clock;
    private final com.cloudpulse.repo.MetricSampleRepository metricSamples;

    public DashboardService(MonitoredServiceRepository services, HealthCheckRepository checks,
                            IncidentRepository incidents, ServiceCatalog catalog, Clock clock,
                            com.cloudpulse.repo.MetricSampleRepository metricSamples) {
        this.services = services;
        this.checks = checks;
        this.incidents = incidents;
        this.catalog = catalog;
        this.clock = clock;
        this.metricSamples = metricSamples;
    }

    public MetricsResponse metrics(Long serviceId, int hours) {
        catalog.find(serviceId);
        int window = Math.max(1, Math.min(hours, 168));
        Instant since = clock.instant().minus(Duration.ofHours(window));
        List<MetricPoint> points = metricSamples.findByServiceIdAndSampledAtAfterOrderBySampledAtAsc(serviceId, since)
                .stream().map(m -> new MetricPoint(m.getMetricName(), m.getValue(), m.getSampledAt())).toList();
        return new MetricsResponse(window, since, points);
    }

    public SummaryResponse summary() {
        Instant now = clock.instant();
        long total = services.count();
        long enabled = services.countByEnabledTrue();
        Double avg = checks.averageSuccessfulResponseTimeSince(now.minus(Duration.ofHours(1)));
        return new SummaryResponse(total, enabled, total - enabled,
                services.countByEnabledTrueAndStatus(ServiceStatus.UP),
                services.countByEnabledTrueAndStatus(ServiceStatus.DOWN),
                services.countByEnabledTrueAndStatus(ServiceStatus.UNKNOWN),
                incidents.countByResolvedAtIsNull(),
                avg == null ? null : Math.round(avg * 10.0) / 10.0,
                now);
    }

    public List<CheckResponse> recentChecks(Long serviceId, int limit) {
        catalog.find(serviceId);
        int size = Math.max(1, Math.min(limit, 200));
        return checks.findByServiceIdOrderByCheckedAtDesc(serviceId, PageRequest.of(0, size)).stream()
                .map(h -> new CheckResponse(h.getId(), h.getCheckedAt(), h.isSuccess(), h.getStatusCode(),
                        h.getResponseTimeMs(), h.getErrorMessage()))
                .toList();
    }

    public LatencyResponse latency(Long serviceId, int hours) {
        catalog.find(serviceId);
        int window = Math.max(1, Math.min(hours, 168));
        Instant since = clock.instant().minus(Duration.ofHours(window));
        List<HealthCheck> rows = checks.findByServiceIdAndCheckedAtAfterOrderByCheckedAtAsc(serviceId, since);
        return new LatencyResponse(window, since, downsample(rows), computeStats(rows));
    }

    public List<IncidentResponse> incidents(boolean activeOnly, int limit) {
        Pageable page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        List<Incident> rows = activeOnly
                ? incidents.findByResolvedAtIsNullOrderByStartedAtDesc(page)
                : incidents.findAllByOrderByStartedAtDesc(page);
        return toResponses(rows);
    }

    public List<IncidentResponse> serviceIncidents(Long serviceId, int limit) {
        catalog.find(serviceId);
        Pageable page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        return toResponses(incidents.findByServiceIdOrderByStartedAtDesc(serviceId, page));
    }

    // ---- helpers -----------------------------------------------------------------------------

    private List<IncidentResponse> toResponses(List<Incident> rows) {
        Set<Long> ids = new HashSet<>();
        rows.forEach(i -> ids.add(i.getServiceId()));
        Map<Long, String> names = new HashMap<>();
        for (MonitoredService s : services.findAllById(ids)) {
            names.put(s.getId(), s.getName());
        }
        Instant now = clock.instant();
        List<IncidentResponse> out = new ArrayList<>(rows.size());
        for (Incident i : rows) {
            Instant end = i.getResolvedAt() != null ? i.getResolvedAt() : now;
            out.add(new IncidentResponse(i.getId(), i.getServiceId(), names.getOrDefault(i.getServiceId(), "(deleted)"),
                    i.getStartedAt(), i.getResolvedAt(), Duration.between(i.getStartedAt(), end).toSeconds(),
                    i.getReason(), i.isActive()));
        }
        return out;
    }

    private static List<LatencyPoint> downsample(List<HealthCheck> rows) {
        int step = Math.max(1, (int) Math.ceil(rows.size() / (double) MAX_POINTS));
        List<LatencyPoint> points = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += step) {
            HealthCheck h = rows.get(i);
            points.add(new LatencyPoint(h.getCheckedAt(), h.getResponseTimeMs(), h.isSuccess()));
        }
        return points;
    }

    static Stats computeStats(List<HealthCheck> rows) {
        long total = rows.size();
        long ok = rows.stream().filter(HealthCheck::isSuccess).count();
        List<Integer> times = new ArrayList<>();
        for (HealthCheck h : rows) {
            if (h.isSuccess() && h.getResponseTimeMs() != null) {
                times.add(h.getResponseTimeMs());
            }
        }
        Collections.sort(times);
        Double availability = total == 0 ? null : Math.round(ok * 10000.0 / total) / 100.0;
        Double avg = null;
        Integer p95 = null;
        Integer max = null;
        if (!times.isEmpty()) {
            long sum = 0;
            for (int t : times) {
                sum += t;
            }
            avg = Math.round(sum * 10.0 / times.size()) / 10.0;
            int rank = (int) Math.ceil(0.95 * times.size()) - 1; // nearest-rank percentile
            p95 = times.get(Math.max(0, rank));
            max = times.get(times.size() - 1);
        }
        return new Stats(total, ok, availability, avg, p95, max);
    }
}
