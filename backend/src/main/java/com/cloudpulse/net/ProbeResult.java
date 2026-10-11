package com.cloudpulse.net;

/**
 * Outcome of a single health probe.
 *
 * @param success        true when the final HTTP status was 2xx/3xx
 * @param statusCode     final HTTP status, or null if no response was received
 * @param responseTimeMs total elapsed time in milliseconds (including redirects)
 * @param error          short error description, or null on success
 */
public record ProbeResult(boolean success, Integer statusCode, int responseTimeMs, String error) {
}
