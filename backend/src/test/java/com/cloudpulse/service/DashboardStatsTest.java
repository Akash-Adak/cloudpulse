package com.cloudpulse.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cloudpulse.domain.HealthCheck;
import com.cloudpulse.domain.MonitoredService;
import com.cloudpulse.web.Dto.Stats;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardStatsTest {

    private static HealthCheck check(boolean ok, Integer ms) {
        HealthCheck h = new HealthCheck();
        h.setSuccess(ok);
        h.setResponseTimeMs(ms);
        h.setCheckedAt(Instant.now());
        return h;
    }

    @Test
    void computesAvailabilityAndLatencyFromRealRows() {
        Stats s = DashboardService.computeStats(List.of(check(true, 100), check(true, 200), check(false, null), check(true, 300)));
        assertEquals(4, s.totalChecks());
        assertEquals(3, s.successfulChecks());
        assertEquals(75.0, s.availabilityPercent());
        assertEquals(200.0, s.avgResponseMs());
        assertEquals(300, s.p95ResponseMs());
        assertEquals(300, s.maxResponseMs());
    }

    @Test
    void returnsNullsWhenThereIsNoData() {
        Stats s = DashboardService.computeStats(List.of());
        assertEquals(0, s.totalChecks());
        assertNull(s.availabilityPercent());
        assertNull(s.avgResponseMs());
        assertNull(s.p95ResponseMs());
    }

    @Test
    void failuresOnlyGivesZeroAvailabilityButNoLatency() {
        Stats s = DashboardService.computeStats(List.of(check(false, 50), check(false, null)));
        assertEquals(0.0, s.availabilityPercent());
        assertNull(s.avgResponseMs());
    }

    @Test
    void schedulerDueLogic() {
        MonitoredService s = new MonitoredService();
        s.setCheckIntervalSeconds(60);
        Instant now = Instant.parse("2026-01-01T00:10:00Z");
        assertEquals(true, HealthCheckScheduler.isDue(s, now));                       // never checked
        s.setLastCheckedAt(now.minusSeconds(30));
        assertEquals(false, HealthCheckScheduler.isDue(s, now));                      // too soon
        s.setLastCheckedAt(now.minusSeconds(60));
        assertEquals(true, HealthCheckScheduler.isDue(s, now));                       // exactly due
    }
}
