package com.cloudpulse.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SsrfGuardTest {

    private final SsrfGuard guard = new SsrfGuard(false);

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/", "http://127.1/", "http://2130706433/", "http://0x7f000001/",
            "http://localhost/", "http://LOCALHOST./", "http://foo.localhost/",
            "http://10.0.0.5/", "http://172.16.3.4/", "http://192.168.1.1/",
            "http://169.254.169.254/latest/meta-data/", "http://metadata.google.internal/",
            "http://100.64.0.1/", "http://0.0.0.0/", "http://224.0.0.1/", "http://255.255.255.255/",
            "http://[::1]/", "http://[fd00:ec2::254]/", "http://[fe80::1]/", "http://[::ffff:127.0.0.1]/",
            "http://[::ffff:169.254.169.254]/", "http://service.internal/", "http://printer.local/"
    })
    void blocksDangerousTargets(String url) {
        assertThrows(UnsafeTargetException.class, () -> guard.validate(url), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/", "file:///etc/passwd", "gopher://example.com/", "javascript:alert(1)",
            "http://user:pass@example.com/", "http://", "not a url", "", "https://example.com:99999/",
            "http://exa mple.com/"
    })
    void rejectsBadSyntaxAndSchemes(String url) {
        assertThrows(UnsafeTargetException.class, () -> guard.parse(url), url);
    }

    @Test
    void rejectsNullAndOverlongUrls() {
        assertThrows(UnsafeTargetException.class, () -> guard.parse(null));
        assertThrows(UnsafeTargetException.class, () -> guard.parse("https://example.com/" + "a".repeat(2100)));
    }

    @Test
    void acceptsPublicHttpAndHttpsSyntax() throws Exception {
        URI uri = guard.parse("https://my-app.vercel.app/health?x=1");
        assertEquals("my-app.vercel.app", uri.getHost());
        assertEquals("http", guard.parse("http://example.com:8080/").getScheme());
    }

    @Test
    void publicAddressesAreAllowed() throws Exception {
        assertFalse(guard.isBlocked(InetAddress.getByName("8.8.8.8")));
        assertFalse(guard.isBlocked(InetAddress.getByName("1.1.1.1")));
        assertFalse(guard.isBlocked(InetAddress.getByName("2606:4700:4700::1111")));
    }

    @Test
    void privateAddressesAreBlocked() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "172.31.255.255", "192.168.0.1", "169.254.169.254",
                "100.100.100.100", "198.18.0.1", "fc00::1", "fd12:3456::1", "::1"}) {
            assertTrue(guard.isBlocked(InetAddress.getByName(ip)), ip);
        }
    }

    @Test
    void devModeAllowsLoopbackButNeverMetadata() throws Exception {
        SsrfGuard dev = new SsrfGuard(true);
        assertFalse(dev.isBlocked(InetAddress.getByName("127.0.0.1")));
        assertFalse(dev.isBlocked(InetAddress.getByName("10.0.0.5")));
        assertTrue(dev.isBlocked(InetAddress.getByName("169.254.169.254")));
        assertTrue(dev.isBlocked(InetAddress.getByName("fd00:ec2::254")));
        assertTrue(dev.isBlocked(InetAddress.getByName("224.0.0.1")));
    }

    @Test
    void resolveSafeRejectsLoopbackHost() {
        assertThrows(UnsafeTargetException.class, () -> guard.resolveSafe("127.0.0.1"));
    }
}
