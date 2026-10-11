package com.cloudpulse.security;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/** Simple in-memory brute-force protection: too many failed logins per client are blocked for a while. */
@Component
public class LoginRateLimiter {

    static final int MAX_FAILURES = 10;
    static final long WINDOW_MS = 15 * 60 * 1000L;

    private record Window(long startMs, int failures) {
    }

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        Window w = windows.get(key);
        if (w == null) {
            return false;
        }
        if (clock.millis() - w.startMs() > WINDOW_MS) {
            windows.remove(key, w);
            return false;
        }
        return w.failures() >= MAX_FAILURES;
    }

    public void recordFailure(String key) {
        long now = clock.millis();
        windows.compute(key, (k, w) -> (w == null || now - w.startMs() > WINDOW_MS)
                ? new Window(now, 1)
                : new Window(w.startMs(), w.failures() + 1));
    }

    public void reset(String key) {
        windows.remove(key);
    }
}
