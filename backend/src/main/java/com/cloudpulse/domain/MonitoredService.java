package com.cloudpulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "monitored_service")
public class MonitoredService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "actuator_metrics_enabled", nullable = false)
    private boolean actuatorMetricsEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @Column(name = "check_interval_seconds", nullable = false)
    private int checkIntervalSeconds;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ServiceStatus status = ServiceStatus.UNKNOWN;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "consecutive_successes", nullable = false)
    private int consecutiveSuccesses;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "last_response_time_ms")
    private Integer lastResponseTimeMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public boolean isActuatorMetricsEnabled() { return actuatorMetricsEnabled; }
    public void setActuatorMetricsEnabled(boolean enabled) { this.actuatorMetricsEnabled = enabled; }
    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public int getCheckIntervalSeconds() { return checkIntervalSeconds; }
    public void setCheckIntervalSeconds(int v) { this.checkIntervalSeconds = v; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int v) { this.timeoutMs = v; }
    public int getFailureThreshold() { return failureThreshold; }
    public void setFailureThreshold(int v) { this.failureThreshold = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public ServiceStatus getStatus() { return status; }
    public void setStatus(ServiceStatus status) { this.status = status; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int v) { this.consecutiveFailures = v; }
    public int getConsecutiveSuccesses() { return consecutiveSuccesses; }
    public void setConsecutiveSuccesses(int v) { this.consecutiveSuccesses = v; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant v) { this.lastCheckedAt = v; }
    public Integer getLastStatusCode() { return lastStatusCode; }
    public void setLastStatusCode(Integer v) { this.lastStatusCode = v; }
    public Integer getLastResponseTimeMs() { return lastResponseTimeMs; }
    public void setLastResponseTimeMs(Integer v) { this.lastResponseTimeMs = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
