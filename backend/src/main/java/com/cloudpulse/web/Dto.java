package com.cloudpulse.web;

import com.cloudpulse.domain.Provider;
import com.cloudpulse.domain.ServiceStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request/response payloads of the REST API. */
public final class Dto {

    private Dto() {
    }

    public record LoginRequest(@NotBlank @Size(max = 100) String username,
                               @NotBlank @Size(max = 200) String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, String username) {
    }

    public record ServiceRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 2048) String url,
            @NotNull Provider provider,
            @Min(10) @Max(3600) Integer checkIntervalSeconds,
            @Min(1000) @Max(30000) Integer timeoutMs,
            @Min(1) @Max(10) Integer failureThreshold,
            Boolean enabled,
            Boolean actuatorMetricsEnabled) {
    }

    public record EnabledRequest(@NotNull Boolean enabled) {
    }

    /** One tick of the heartbeat strip: oldest to newest. */
    public record RecentCheck(boolean success, Integer responseTimeMs) {
    }

    public record ServiceResponse(
            Long id, String name, String url, Provider provider,
            int checkIntervalSeconds, int timeoutMs, int failureThreshold,
            boolean enabled, ServiceStatus status, int consecutiveFailures,
            Instant lastCheckedAt, Integer lastStatusCode, Integer lastResponseTimeMs,
            Instant createdAt, boolean actuatorMetricsEnabled, List<RecentCheck> recent) {
    }

    public record CheckResponse(Long id, Instant checkedAt, boolean success, Integer statusCode,
                                Integer responseTimeMs, String errorMessage) {
    }

    public record CheckNowResponse(boolean success, Integer statusCode, int responseTimeMs, String error) {
    }

    public record LatencyPoint(Instant t, Integer ms, boolean success) {
    }

    /** All figures are computed from stored checks; null means "not enough data". */
    public record Stats(long totalChecks, long successfulChecks, Double availabilityPercent,
                        Double avgResponseMs, Integer p95ResponseMs, Integer maxResponseMs) {
    }

    public record LatencyResponse(int hours, Instant since, List<LatencyPoint> points, Stats stats) {
    }

    public record IncidentResponse(Long id, Long serviceId, String serviceName, Instant startedAt,
                                   Instant resolvedAt, Long durationSeconds, String reason, boolean active) {
    }

    public record SummaryResponse(long totalServices, long enabledServices, long disabledServices,
                                  long up, long down, long unknown, long activeIncidents,
                                  Double avgResponseMs1h, Instant generatedAt) {
    }

    public record MetricPoint(String name, double value, Instant sampledAt) {}
    public record MetricsResponse(int hours, Instant since, List<MetricPoint> points) {}

    public record ApiError(Instant timestamp, int status, String error, String message,
                           Map<String, String> fieldErrors) {
    }
}
