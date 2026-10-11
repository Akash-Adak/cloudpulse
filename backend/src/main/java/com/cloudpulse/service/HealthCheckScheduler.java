package com.cloudpulse.service;

import com.cloudpulse.domain.MonitoredService;
import com.cloudpulse.repo.HealthCheckRepository;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cloudpulse.repo.MonitoredServiceRepository;
import com.cloudpulse.repo.MetricSampleRepository;

/**
 * Backend-driven scheduler: monitoring continues whether or not anyone has the dashboard open.
 *
 * <p>A lightweight tick (default 5 s) finds services that are due, based on each service's own
 * interval, and runs their probes on virtual threads. A service is never probed twice at once.
 */
@Component
@ConditionalOnProperty(name = "cloudpulse.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class HealthCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckScheduler.class);

    private final MonitoredServiceRepository services;
    private final HealthCheckRepository checks;
    private final CheckRunner runner;
    private final Clock clock;
    private final int retentionDays;
    private final MetricSampleRepository metricSamples;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public HealthCheckScheduler(MonitoredServiceRepository services, HealthCheckRepository checks,
                                CheckRunner runner, Clock clock,
                                @Value("${cloudpulse.retention.days:30}") int retentionDays,
                                MetricSampleRepository metricSamples) {
        this.services = services;
        this.checks = checks;
        this.runner = runner;
        this.clock = clock;
        this.retentionDays = Math.max(1, retentionDays);
        this.metricSamples = metricSamples;
    }

    @Scheduled(fixedDelayString = "${cloudpulse.scheduler.tick-ms:5000}", initialDelayString = "${cloudpulse.scheduler.initial-delay-ms:5000}")
    public void tick() {
        Instant now = clock.instant();
        try {
            for (MonitoredService service : services.findByEnabledTrue()) {
                if (!isDue(service, now) || !inFlight.add(service.getId())) {
                    continue;
                }
                Long id = service.getId();
                executor.submit(() -> {
                    try {
                        runner.runCheck(id);
                    } catch (Exception e) {
                        log.error("Health check for service {} failed unexpectedly", id, e);
                    } finally {
                        inFlight.remove(id);
                    }
                });
            }
        } catch (Exception e) {
            log.error("Scheduler tick failed", e);
        }
    }

    /** Deletes check history older than the retention window (keeps the database small). */
    @Scheduled(cron = "${cloudpulse.retention.cron:0 30 3 * * *}")
    public void purgeOldChecks() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(retentionDays));
        int deleted = checks.deleteOlderThan(cutoff);
        metricSamples.deleteOlderThan(cutoff);
        log.info("Retention: deleted {} health checks older than {} days", deleted, retentionDays);
    }

    static boolean isDue(MonitoredService service, Instant now) {
        Instant last = service.getLastCheckedAt();
        return last == null || !now.isBefore(last.plusSeconds(service.getCheckIntervalSeconds()));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
