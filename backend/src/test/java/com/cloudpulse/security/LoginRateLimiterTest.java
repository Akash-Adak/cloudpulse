package com.cloudpulse.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    static final class MutableClock extends Clock {
        long now = 1_000_000L;

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        @Override public long millis() { return now; }
    }

    @Test
    void blocksAfterTooManyFailuresThenRecovers() {
        MutableClock clock = new MutableClock();
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES - 1; i++) {
            limiter.recordFailure("1.2.3.4");
        }
        assertFalse(limiter.isBlocked("1.2.3.4"));
        limiter.recordFailure("1.2.3.4");
        assertTrue(limiter.isBlocked("1.2.3.4"));
        assertFalse(limiter.isBlocked("5.6.7.8"));

        clock.now += LoginRateLimiter.WINDOW_MS + 1;
        assertFalse(limiter.isBlocked("1.2.3.4"));
    }

    @Test
    void successfulLoginClearsFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter(new MutableClock());
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("x");
        }
        limiter.reset("x");
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES - 1; i++) {
            limiter.recordFailure("x");
        }
        assertFalse(limiter.isBlocked("x"));
    }
}
