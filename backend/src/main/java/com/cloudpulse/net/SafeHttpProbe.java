package com.cloudpulse.net;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Minimal, SSRF-hardened HTTP/1.1 prober.
 *
 * <p>Why not a normal HTTP client? Normal clients resolve DNS internally, so a hostile DNS server
 * can answer with a public IP during validation and a private IP when the client connects
 * (DNS rebinding). Here we resolve once, validate every address with {@link SsrfGuard}, and then
 * open the TCP socket directly to the validated {@link InetAddress}. TLS is layered on top with the
 * original host name for SNI and certificate verification, so HTTPS stays properly verified.
 *
 * <p>Redirects are followed manually (max {@code maxRedirects}); every hop goes through the
 * guard again, so a public site cannot redirect the monitor to an internal address.
 *
 * <p>Only the status line and headers are read; response bodies are never downloaded or stored.
 */
public final class SafeHttpProbe {

    private static final int MAX_LINE_BYTES = 8192;
    private static final int MAX_HEADER_LINES = 100;
    private static final int MAX_ERROR_LENGTH = 500;

    private final SsrfGuard guard;
    private final int maxRedirects;

    public SafeHttpProbe(SsrfGuard guard, int maxRedirects) {
        this.guard = guard;
        this.maxRedirects = Math.max(0, maxRedirects);
    }

    /** Performs one health probe. Never throws; failures are reported in the result. */
    public ProbeResult probe(String url, int timeoutMs) {
        long startNanos = System.nanoTime();
        long deadlineNanos = startNanos + timeoutMs * 1_000_000L;
        String current = url;
        try {
            for (int hop = 0; hop <= maxRedirects; hop++) {
                URI uri = guard.parse(current);
                Response response = fetchOnce(uri, deadlineNanos);
                boolean redirect = response.status >= 300 && response.status < 400 && response.location != null;
                if (redirect) {
                    if (hop == maxRedirects) {
                        return failure(null, startNanos, "Too many redirects");
                    }
                    current = uri.resolve(response.location).toString();
                    continue;
                }
                boolean ok = response.status >= 200 && response.status < 400;
                return new ProbeResult(ok, response.status, elapsedMs(startNanos), ok ? null : "HTTP " + response.status);
            }
            return failure(null, startNanos, "Too many redirects");
        } catch (UnsafeTargetException e) {
            return failure(null, startNanos, "Blocked target: " + e.getMessage());
        } catch (SocketTimeoutException e) {
            return failure(null, startNanos, "Timed out after " + timeoutMs + " ms");
        } catch (IOException e) {
            return failure(null, startNanos, "Connection error: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " - " + e.getMessage() : ""));
        } catch (IllegalArgumentException e) {
            return failure(null, startNanos, "Invalid redirect location");
        }
    }

    // ---- internals ---------------------------------------------------------------------------

    private record Response(int status, String location) {
    }

    private Response fetchOnce(URI uri, long deadlineNanos) throws IOException, UnsafeTargetException {
        boolean https = uri.getScheme().equalsIgnoreCase("https");
        String host = uri.getHost();
        int port = uri.getPort() != -1 ? uri.getPort() : (https ? 443 : 80);

        // Resolve + validate immediately before connecting; connect to the validated address only.
        List<InetAddress> addresses = guard.resolveSafe(host);

        Socket raw = null;
        IOException lastError = null;
        for (InetAddress address : addresses) {
            Socket candidate = new Socket();
            try {
                candidate.connect(new InetSocketAddress(address, port), remainingMs(deadlineNanos));
                raw = candidate;
                break;
            } catch (IOException e) {
                lastError = e;
                closeQuietly(candidate);
            }
        }
        if (raw == null) {
            throw lastError != null ? lastError : new IOException("No reachable address");
        }

        Socket socket = raw;
        try {
            raw.setSoTimeout(remainingMs(deadlineNanos));
            if (https) {
                socket = startTls(raw, host, port);
            }
            writeRequest(socket.getOutputStream(), uri, host, port, https);
            InputStream in = new BufferedInputStream(socket.getInputStream(), 4096);

            String statusLine = readLine(in, socket, deadlineNanos);
            int status = parseStatus(statusLine);
            String location = null;
            for (int i = 0; i < MAX_HEADER_LINES; i++) {
                String header = readLine(in, socket, deadlineNanos);
                if (header.isEmpty()) {
                    break;
                }
                if (header.regionMatches(true, 0, "location:", 0, 9)) {
                    location = header.substring(9).trim();
                }
            }
            return new Response(status, location);
        } finally {
            closeQuietly(socket);
            closeQuietly(raw);
        }
    }

    private static SSLSocket startTls(Socket raw, String host, int port) throws IOException {
        String bareHost = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl = (SSLSocket) factory.createSocket(raw, bareHost, port, true);
        SSLParameters params = ssl.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS"); // verify certificate matches host name
        if (!isIpLiteral(bareHost)) {
            params.setServerNames(List.of(new SNIHostName(bareHost)));
        }
        ssl.setSSLParameters(params);
        ssl.startHandshake();
        return ssl;
    }

    private static void writeRequest(OutputStream out, URI uri, String host, int port, boolean https) throws IOException {
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (uri.getRawQuery() != null) {
            path = path + "?" + uri.getRawQuery();
        }
        boolean defaultPort = (https && port == 443) || (!https && port == 80);
        String hostHeader = defaultPort ? host : host + ":" + port;
        if (path.indexOf('\r') >= 0 || path.indexOf('\n') >= 0 || hostHeader.indexOf('\r') >= 0 || hostHeader.indexOf('\n') >= 0) {
            throw new IOException("Illegal characters in request");
        }
        String request = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + hostHeader + "\r\n"
                + "User-Agent: CloudPulse-Monitor/1.0\r\n"
                + "Accept: */*\r\n"
                + "Connection: close\r\n\r\n";
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static String readLine(InputStream in, Socket socket, long deadlineNanos) throws IOException {
        socket.setSoTimeout(remainingMs(deadlineNanos));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        while (true) {
            int c = in.read();
            if (c == -1) {
                if (buffer.size() == 0) {
                    throw new IOException("Connection closed before a full response was received");
                }
                break;
            }
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                buffer.write(c);
            }
            if (buffer.size() > MAX_LINE_BYTES) {
                throw new IOException("Response header line too long");
            }
        }
        return buffer.toString(StandardCharsets.ISO_8859_1);
    }

    private static int parseStatus(String line) throws IOException {
        if (!line.startsWith("HTTP/")) {
            throw new IOException("Malformed HTTP response");
        }
        String[] parts = line.split(" ", 3);
        if (parts.length < 2) {
            throw new IOException("Malformed HTTP status line");
        }
        try {
            int status = Integer.parseInt(parts[1].trim());
            if (status < 100 || status > 599) {
                throw new IOException("Invalid HTTP status code");
            }
            return status;
        } catch (NumberFormatException e) {
            throw new IOException("Malformed HTTP status code");
        }
    }

    private static int remainingMs(long deadlineNanos) throws SocketTimeoutException {
        long remaining = (deadlineNanos - System.nanoTime()) / 1_000_000L;
        if (remaining <= 0) {
            throw new SocketTimeoutException("deadline exceeded");
        }
        return (int) Math.min(remaining, Integer.MAX_VALUE);
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private static int elapsedMs(long startNanos) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private static ProbeResult failure(Integer status, long startNanos, String message) {
        String msg = message == null ? "Unknown error" : message;
        if (msg.length() > MAX_ERROR_LENGTH) {
            msg = msg.substring(0, MAX_ERROR_LENGTH);
        }
        return new ProbeResult(false, status, elapsedMs(startNanos), msg);
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }
}
