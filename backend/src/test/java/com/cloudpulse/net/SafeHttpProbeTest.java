package com.cloudpulse.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Exercises the probe against a throw-away local HTTP server (dev guard permits loopback). */
class SafeHttpProbeTest {

    private static HttpServer server;
    private static String base;
    private static final AtomicInteger hits = new AtomicInteger();

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", ex -> reply(ex, 200, null));
        server.createContext("/boom", ex -> reply(ex, 500, null));
        server.createContext("/missing", ex -> reply(ex, 404, null));
        server.createContext("/hop", ex -> reply(ex, 302, "/ok"));
        server.createContext("/loop", ex -> reply(ex, 302, "/loop"));
        server.createContext("/to-metadata", ex -> reply(ex, 302, "http://169.254.169.254/latest/meta-data/"));
        server.createContext("/to-ftp", ex -> reply(ex, 302, "ftp://example.com/"));
        server.createContext("/slow", ex -> {
            hits.incrementAndGet();
            try {
                Thread.sleep(2500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            reply(ex, 200, null);
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String location) throws java.io.IOException {
        if (location != null) {
            ex.getResponseHeaders().add("Location", location);
        }
        ex.sendResponseHeaders(status, -1);
        ex.close();
    }

    private final SafeHttpProbe devProbe = new SafeHttpProbe(new SsrfGuard(true), 3);
    private final SafeHttpProbe strictProbe = new SafeHttpProbe(new SsrfGuard(false), 3);

    @Test
    void reportsHealthyService() {
        ProbeResult r = devProbe.probe(base + "/ok", 3000);
        assertTrue(r.success());
        assertEquals(200, r.statusCode());
        assertNull(r.error());
        assertTrue(r.responseTimeMs() >= 0);
    }

    @Test
    void reportsServerErrorsAsFailures() {
        ProbeResult r = devProbe.probe(base + "/boom", 3000);
        assertFalse(r.success());
        assertEquals(500, r.statusCode());
        assertEquals("HTTP 500", r.error());
        assertEquals(404, devProbe.probe(base + "/missing", 3000).statusCode());
    }

    @Test
    void followsSafeRedirects() {
        ProbeResult r = devProbe.probe(base + "/hop", 3000);
        assertTrue(r.success());
        assertEquals(200, r.statusCode());
    }

    @Test
    void stopsRedirectLoops() {
        ProbeResult r = devProbe.probe(base + "/loop", 3000);
        assertFalse(r.success());
        assertEquals("Too many redirects", r.error());
    }

    @Test
    void blocksRedirectToMetadataEndpoint() {
        ProbeResult r = devProbe.probe(base + "/to-metadata", 3000);
        assertFalse(r.success());
        assertTrue(r.error().startsWith("Blocked target"), r.error());
    }

    @Test
    void blocksRedirectToNonHttpScheme() {
        ProbeResult r = devProbe.probe(base + "/to-ftp", 3000);
        assertFalse(r.success());
        assertTrue(r.error().startsWith("Blocked target"), r.error());
    }

    @Test
    void timesOutSlowServers() {
        long start = System.nanoTime();
        ProbeResult r = devProbe.probe(base + "/slow", 800);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertFalse(r.success());
        assertNull(r.statusCode());
        assertNotNull(r.error());
        assertTrue(r.error().startsWith("Timed out"), r.error());
        assertTrue(elapsedMs < 2000, "should abort near the timeout, took " + elapsedMs);
    }

    @Test
    void strictModeRefusesLoopbackTargets() {
        ProbeResult r = strictProbe.probe(base + "/ok", 3000);
        assertFalse(r.success());
        assertTrue(r.error().startsWith("Blocked target"), r.error());
    }

    @Test
    void reportsConnectionRefused() {
        ProbeResult r = devProbe.probe("http://127.0.0.1:1/", 2000);
        assertFalse(r.success());
        assertNotNull(r.error());
    }
}
