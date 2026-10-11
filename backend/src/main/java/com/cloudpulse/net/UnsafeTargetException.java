package com.cloudpulse.net;

/** Thrown when a monitoring target is rejected by the SSRF guard. */
public class UnsafeTargetException extends Exception {
    private static final long serialVersionUID = 1L;

    public UnsafeTargetException(String message) {
        super(message);
    }
}
