package com.cloudpulse.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Server-Side Request Forgery (SSRF) protection for user-supplied monitoring targets.
 *
 * <p>Rules enforced:
 * <ul>
 *   <li>Only http/https URLs, no embedded credentials (user:pass@host), valid port.</li>
 *   <li>Blocked hostnames (localhost, *.internal, *.local, cloud metadata names).</li>
 *   <li>Every IP address the hostname resolves to must be public. Loopback, private (RFC 1918),
 *       link-local (169.254.0.0/16 incl. the cloud metadata IP), CGNAT, multicast, reserved,
 *       unique-local IPv6 and other special ranges are rejected.</li>
 * </ul>
 *
 * <p>This class is intentionally free of framework dependencies so it can be unit-tested in
 * isolation. {@link SafeHttpProbe} calls {@link #resolveSafe(String)} immediately before opening
 * a socket and connects to the already-validated address, which closes the DNS-rebinding window.
 */
public final class SsrfGuard {

    private static final int MAX_URL_LENGTH = 2048;

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "metadata", "metadata.google.internal", "instance-data");

    private static final List<String> BLOCKED_SUFFIXES = List.of(
            ".localhost", ".local", ".internal", ".localdomain", ".home.arpa");

    private final boolean allowPrivateTargets;

    /**
     * @param allowPrivateTargets DEV/TEST ONLY. Permits loopback and private ranges so a local
     *                            mock service can be monitored. Link-local addresses (cloud
     *                            metadata) and other special ranges stay blocked regardless.
     */
    public SsrfGuard(boolean allowPrivateTargets) {
        this.allowPrivateTargets = allowPrivateTargets;
    }

    /** Syntax-level validation. Does not touch the network. */
    public URI parse(String raw) throws UnsafeTargetException {
        if (raw == null || raw.isBlank()) {
            throw new UnsafeTargetException("URL is required");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw new UnsafeTargetException("URL is too long");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c <= 0x20 || c == 0x7f) {
                throw new UnsafeTargetException("URL contains illegal characters");
            }
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new UnsafeTargetException("URL is malformed");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new UnsafeTargetException("Only http and https URLs are allowed");
        }
        if (uri.getRawUserInfo() != null) {
            throw new UnsafeTargetException("URLs with embedded credentials are not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UnsafeTargetException("URL must contain a valid host");
        }
        int port = uri.getPort();
        if (port != -1 && (port < 1 || port > 65535)) {
            throw new UnsafeTargetException("Port is out of range");
        }
        if (!allowPrivateTargets) {
            String h = normalizeHost(host);
            if (BLOCKED_HOSTS.contains(h) || BLOCKED_SUFFIXES.stream().anyMatch(h::endsWith)) {
                throw new UnsafeTargetException("Host name is not allowed");
            }
        }
        return uri;
    }

    /** Full validation used when a service is registered or updated (syntax + DNS). */
    public URI validate(String raw) throws UnsafeTargetException {
        URI uri = parse(raw);
        resolveSafe(uri.getHost());
        return uri;
    }

    /**
     * Resolves a host and returns its addresses only if <b>all</b> of them are allowed.
     * If any resolved address is forbidden the whole host is rejected (prevents a mixed
     * public/private DNS answer from slipping through).
     */
    public List<InetAddress> resolveSafe(String host) throws UnsafeTargetException {
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(stripBrackets(host));
        } catch (UnknownHostException e) {
            throw new UnsafeTargetException("Host could not be resolved");
        }
        if (resolved.length == 0) {
            throw new UnsafeTargetException("Host could not be resolved");
        }
        List<InetAddress> ok = new ArrayList<>(resolved.length);
        for (InetAddress address : resolved) {
            if (isBlocked(address)) {
                throw new UnsafeTargetException("Target resolves to a forbidden address range");
            }
            ok.add(address);
        }
        return ok;
    }

    /** True when the address must not be contacted. */
    public boolean isBlocked(InetAddress a) {
        if (isAlwaysBlocked(a)) {
            return true;
        }
        return !allowPrivateTargets && isPrivateRange(a);
    }

    // ---- address classification -------------------------------------------------------------

    /** Ranges that are never allowed, even in dev mode (metadata, link-local, multicast, reserved). */
    private static boolean isAlwaysBlocked(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int b0 = u(b[0]);
            int b1 = u(b[1]);
            int b2 = u(b[2]);
            if (b0 == 0) return true;                                   // 0.0.0.0/8
            if (b0 >= 224) return true;                                 // multicast + reserved + broadcast
            if (b0 == 192 && b1 == 0 && b2 == 0) return true;           // 192.0.0.0/24 IETF protocol
            if (b0 == 192 && b1 == 0 && b2 == 2) return true;           // TEST-NET-1
            if (b0 == 198 && (b1 & 0xFE) == 18) return true;            // 198.18.0.0/15 benchmarking
            if (b0 == 198 && b1 == 51 && b2 == 100) return true;        // TEST-NET-2
            if (b0 == 203 && b1 == 0 && b2 == 113) return true;         // TEST-NET-3
            return false;
        }
        if (b.length == 16) {
            // ::/96 (IPv4-compatible, includes :: and ::1)
            if (allZero(b, 0, 12)) return true;
            // ::ffff:0:0/96 IPv4-mapped (Java normally converts these; check defensively)
            if (allZero(b, 0, 10) && u(b[10]) == 0xFF && u(b[11]) == 0xFF) {
                return isBlockedEmbeddedV4(b, 12);
            }
            // 64:ff9b::/96 NAT64
            if (u(b[0]) == 0x00 && u(b[1]) == 0x64 && u(b[2]) == 0xFF && u(b[3]) == 0x9B && allZero(b, 4, 12)) {
                return true;
            }
            if (u(b[0]) == 0x20 && u(b[1]) == 0x02) return true;        // 2002::/16 6to4
            if (u(b[0]) == 0x20 && u(b[1]) == 0x01 && u(b[2]) == 0x0D && u(b[3]) == 0xB8) {
                return true;                                            // 2001:db8::/32 documentation
            }
            // fd00:ec2::/32 AWS IPv6 metadata lives inside ULA; keep it blocked even in dev mode
            if (u(b[0]) == 0xFD && u(b[1]) == 0x00 && u(b[2]) == 0x0E && u(b[3]) == 0xC2) return true;
        }
        return false;
    }

    /** Private/internal ranges that are only blocked when private targets are disallowed. */
    private static boolean isPrivateRange(InetAddress a) {
        if (a.isLoopbackAddress() || a.isSiteLocalAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int b0 = u(b[0]);
            int b1 = u(b[1]);
            return b0 == 100 && (b1 & 0xC0) == 64;                      // 100.64.0.0/10 CGNAT
        }
        if (b.length == 16) {
            return (u(b[0]) & 0xFE) == 0xFC;                            // fc00::/7 unique local
        }
        return false;
    }

    private static boolean isBlockedEmbeddedV4(byte[] b, int offset) {
        try {
            InetAddress v4 = InetAddress.getByAddress(new byte[]{b[offset], b[offset + 1], b[offset + 2], b[offset + 3]});
            return isAlwaysBlocked(v4) || isPrivateRange(v4);
        } catch (UnknownHostException e) {
            return true;
        }
    }

    private static boolean allZero(byte[] b, int from, int to) {
        for (int i = from; i < to; i++) {
            if (b[i] != 0) return false;
        }
        return true;
    }

    private static int u(byte b) {
        return b & 0xFF;
    }

    private static String normalizeHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        while (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        return h;
    }

    private static String stripBrackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }
}
