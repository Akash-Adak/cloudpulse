package com.cloudpulse.service;

import com.cloudpulse.domain.MetricSample;
import com.cloudpulse.domain.MonitoredService;
import com.cloudpulse.net.SsrfGuard;
import com.cloudpulse.net.UnsafeTargetException;
import com.cloudpulse.repo.MetricSampleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Collects numeric Spring Boot Actuator metrics exposed by a monitored service. */
@Service
public class MetricsCollector {
    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);
    private static final List<String> METRICS = List.of(
            "system.cpu.usage", "process.cpu.usage", "jvm.memory.used", "jvm.memory.max",
            "jvm.threads.live", "http.server.requests", "hikaricp.connections.active",
            "hikaricp.connections.max", "disk.free");

    private final MetricSampleRepository samples;
    private final SsrfGuard guard;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public MetricsCollector(MetricSampleRepository samples, SsrfGuard guard, ObjectMapper mapper) {
        this.samples = samples;
        this.guard = guard;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Transactional
    public void collect(MonitoredService service) {
        URI endpoint;
        try {
            URI base = guard.parse(service.getUrl());
            endpoint = actuatorEndpoint(base);
            guard.validate(endpoint.toString());
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(service.getTimeoutMs()))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Actuator metrics index for service {} returned HTTP {}", service.getId(), response.statusCode());
                return;
            }
            JsonNode names = mapper.readTree(response.body()).path("names");
            Instant now = Instant.now();
            for (String metric : METRICS) {
                if (!contains(names, metric)) continue;
                URI metricUri = URI.create(endpoint + "/" + metric);
                HttpResponse<String> detail = client.send(HttpRequest.newBuilder(metricUri)
                        .timeout(Duration.ofMillis(service.getTimeoutMs())).header("Accept", "application/json").GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (detail.statusCode() != 200) {
                    log.debug("Actuator metric {} for service {} returned HTTP {}", metric, service.getId(), detail.statusCode());
                    continue;
                }
                JsonNode measurements = mapper.readTree(detail.body()).path("measurements");
                for (JsonNode measurement : measurements) {
                    JsonNode value = measurement.get("value");
                    if (value != null && value.isNumber() && Double.isFinite(value.asDouble())) {
                        samples.save(new MetricSample(service.getId(), metric, value.asDouble(), now));
                        break;
                    }
                }
            }
        } catch (UnsafeTargetException | java.io.IOException | InterruptedException | java.net.URISyntaxException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Actuator metrics unavailable for service {}: {}", service.getId(), e.getMessage());
        }
    }

    private static boolean contains(JsonNode names, String metric) {
        for (JsonNode name : names) if (metric.equals(name.asText())) return true;
        return false;
    }

    private static URI actuatorEndpoint(URI base) throws java.net.URISyntaxException {
        String path = base.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            path = "/actuator/metrics";
        } else {
            path = path.replaceAll("/+$", "");
            int actuatorIndex = path.indexOf("/actuator/");
            if (actuatorIndex >= 0) {
                path = path.substring(0, actuatorIndex) + "/actuator/metrics";
                return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), path, null, null);
            }
            int lastSlash = path.lastIndexOf('/');
            String lastSegment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
            if ("health".equalsIgnoreCase(lastSegment) || "info".equalsIgnoreCase(lastSegment)) {
                path = lastSlash == 0 ? "" : path.substring(0, lastSlash);
            }
            path = (path.isBlank() ? "" : path) + "/actuator/metrics";
        }
        return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), path, null, null);
    }
}
