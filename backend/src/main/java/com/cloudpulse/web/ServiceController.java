package com.cloudpulse.web;

import com.cloudpulse.net.ProbeResult;
import com.cloudpulse.net.UnsafeTargetException;
import com.cloudpulse.service.CheckRunner;
import com.cloudpulse.service.DashboardService;
import com.cloudpulse.service.ResourceNotFoundException;
import com.cloudpulse.service.ServiceCatalog;
import com.cloudpulse.web.Dto.CheckNowResponse;
import com.cloudpulse.web.Dto.CheckResponse;
import com.cloudpulse.web.Dto.EnabledRequest;
import com.cloudpulse.web.Dto.IncidentResponse;
import com.cloudpulse.web.Dto.LatencyResponse;
import com.cloudpulse.web.Dto.MetricsResponse;
import com.cloudpulse.web.Dto.ServiceRequest;
import com.cloudpulse.web.Dto.ServiceResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceCatalog catalog;
    private final DashboardService dashboard;
    private final CheckRunner runner;

    public ServiceController(ServiceCatalog catalog, DashboardService dashboard, CheckRunner runner) {
        this.catalog = catalog;
        this.dashboard = dashboard;
        this.runner = runner;
    }

    @GetMapping
    public List<ServiceResponse> list() {
        return catalog.list();
    }

    @GetMapping("/{id}")
    public ServiceResponse get(@PathVariable Long id) {
        return catalog.get(id);
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> create(@Valid @RequestBody ServiceRequest request) throws UnsafeTargetException {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.create(request));
    }

    @PutMapping("/{id}")
    public ServiceResponse update(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) throws UnsafeTargetException {
        return catalog.update(id, request);
    }

    @PatchMapping("/{id}/enabled")
    public ServiceResponse setEnabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest request) {
        return catalog.setEnabled(id, request.enabled());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        catalog.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Runs a probe immediately and records it like a scheduled check. */
    @PostMapping("/{id}/check-now")
    public CheckNowResponse checkNow(@PathVariable Long id) {
        ProbeResult result = runner.runCheck(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service " + id + " was not found"));
        return new CheckNowResponse(result.success(), result.statusCode(), result.responseTimeMs(), result.error());
    }

    @GetMapping("/{id}/checks")
    public List<CheckResponse> checks(@PathVariable Long id, @RequestParam(defaultValue = "50") int limit) {
        return dashboard.recentChecks(id, limit);
    }

    @GetMapping("/{id}/latency")
    public LatencyResponse latency(@PathVariable Long id, @RequestParam(defaultValue = "24") int hours) {
        return dashboard.latency(id, hours);
    }

    @GetMapping("/{id}/metrics")
    public MetricsResponse metrics(@PathVariable Long id, @RequestParam(defaultValue = "24") int hours) {
        return dashboard.metrics(id, hours);
    }

    @GetMapping("/{id}/incidents")
    public List<IncidentResponse> incidents(@PathVariable Long id, @RequestParam(defaultValue = "20") int limit) {
        return dashboard.serviceIncidents(id, limit);
    }
}
