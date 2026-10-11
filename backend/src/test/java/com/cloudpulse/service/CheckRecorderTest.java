package com.cloudpulse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudpulse.domain.Incident;
import com.cloudpulse.domain.MonitoredService;
import com.cloudpulse.domain.Provider;
import com.cloudpulse.domain.ServiceStatus;
import com.cloudpulse.net.ProbeResult;
import com.cloudpulse.repo.HealthCheckRepository;
import com.cloudpulse.repo.IncidentRepository;
import com.cloudpulse.repo.MonitoredServiceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for the up/down state machine (repositories are mocked). */
class CheckRecorderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final ProbeResult OK = new ProbeResult(true, 200, 120, null);
    private static final ProbeResult FAIL = new ProbeResult(false, 503, 80, "HTTP 503");

    private MonitoredServiceRepository services;
    private HealthCheckRepository checks;
    private IncidentRepository incidents;
    private CheckRecorder recorder;
    private MonitoredService service;

    @BeforeEach
    void setUp() {
        services = mock(MonitoredServiceRepository.class);
        checks = mock(HealthCheckRepository.class);
        incidents = mock(IncidentRepository.class);
        recorder = new CheckRecorder(services, checks, incidents, Clock.fixed(NOW, ZoneOffset.UTC), 1);

        service = new MonitoredService();
        service.setId(1L);
        service.setName("Portfolio");
        service.setUrl("https://example.com");
        service.setProvider(Provider.VERCEL);
        service.setFailureThreshold(3);

        when(services.findById(1L)).thenReturn(Optional.of(service));
        when(incidents.findFirstByServiceIdAndResolvedAtIsNull(1L)).thenReturn(Optional.empty());
        when(incidents.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void firstSuccessMarksServiceUp() {
        var outcome = recorder.record(1L, OK).orElseThrow();
        assertEquals(ServiceStatus.UP, service.getStatus());
        assertNull(outcome.opened());
        assertEquals(120, service.getLastResponseTimeMs());
        verify(checks, times(1)).save(any());
    }

    @Test
    void singleTransientFailureDoesNotOpenIncident() {
        recorder.record(1L, OK);
        var outcome = recorder.record(1L, FAIL).orElseThrow();
        assertEquals(ServiceStatus.UP, service.getStatus());
        assertNull(outcome.opened());
        verify(incidents, never()).save(any());
    }

    @Test
    void reachingThresholdOpensExactlyOneIncident() {
        recorder.record(1L, OK);
        Incident opened = null;
        for (int i = 0; i < 6; i++) {
            var outcome = recorder.record(1L, FAIL).orElseThrow();
            if (outcome.opened() != null) {
                opened = outcome.opened();
            }
        }
        assertEquals(ServiceStatus.DOWN, service.getStatus());
        assertNotNull(opened);
        assertEquals("HTTP 503", opened.getReason());
        assertEquals(NOW, opened.getStartedAt());
        verify(incidents, times(1)).save(any(Incident.class)); // deduplicated
    }

    @Test
    void successInBetweenResetsTheFailureStreak() {
        recorder.record(1L, FAIL);
        recorder.record(1L, FAIL);
        recorder.record(1L, OK);
        recorder.record(1L, FAIL);
        recorder.record(1L, FAIL);
        assertEquals(ServiceStatus.UP, service.getStatus());
        assertEquals(2, service.getConsecutiveFailures());
        verify(incidents, never()).save(any());
    }

    @Test
    void recoveryResolvesTheOpenIncident() {
        Incident open = new Incident();
        open.setId(9L);
        open.setServiceId(1L);
        open.setStartedAt(NOW.minusSeconds(300));
        service.setStatus(ServiceStatus.DOWN);
        service.setConsecutiveFailures(5);
        when(incidents.findFirstByServiceIdAndResolvedAtIsNull(1L)).thenReturn(Optional.of(open));

        var outcome = recorder.record(1L, OK).orElseThrow();

        assertEquals(ServiceStatus.UP, service.getStatus());
        assertNotNull(outcome.resolved());
        assertEquals(NOW, outcome.resolved().getResolvedAt());
        assertFalse(outcome.resolved().isActive());
        assertEquals(0, service.getConsecutiveFailures());
    }

    @Test
    void recoveryThresholdRequiresConsecutiveSuccesses() {
        recorder = new CheckRecorder(services, checks, incidents, Clock.fixed(NOW, ZoneOffset.UTC), 2);
        service.setStatus(ServiceStatus.DOWN);
        Incident open = new Incident();
        open.setServiceId(1L);
        open.setStartedAt(NOW.minusSeconds(60));
        when(incidents.findFirstByServiceIdAndResolvedAtIsNull(1L)).thenReturn(Optional.of(open));

        assertNull(recorder.record(1L, OK).orElseThrow().resolved());
        assertEquals(ServiceStatus.DOWN, service.getStatus());
        assertTrue(recorder.record(1L, OK).orElseThrow().resolved() != null);
        assertEquals(ServiceStatus.UP, service.getStatus());
    }

    @Test
    void deletedServiceIsIgnored() {
        when(services.findById(2L)).thenReturn(Optional.empty());
        assertTrue(recorder.record(2L, OK).isEmpty());
        verify(checks, never()).save(any());
    }
}
