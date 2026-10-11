package com.cloudpulse.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Single administrator account whose credentials come from environment variables.
 * The password is BCrypt-hashed in memory at startup; the plain text is not kept.
 */
@Component
public class AdminAuthenticator {

    private final String username;
    private final String passwordHash;
    private final PasswordEncoder encoder;

    public AdminAuthenticator(@Value("${cloudpulse.admin.username}") String username,
                              @Value("${cloudpulse.admin.password}") String password,
                              PasswordEncoder encoder) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("CLOUDPULSE_ADMIN_USERNAME must be set");
        }
        if (password == null || password.length() < 10) {
            throw new IllegalStateException("CLOUDPULSE_ADMIN_PASSWORD must be set and at least 10 characters long");
        }
        this.username = username;
        this.encoder = encoder;
        this.passwordHash = encoder.encode(password);
    }

    public boolean authenticate(String candidateUser, String candidatePassword) {
        // Always run both checks so timing does not reveal whether the username was right.
        boolean userOk = MessageDigest.isEqual(
                username.getBytes(StandardCharsets.UTF_8),
                (candidateUser == null ? "" : candidateUser).getBytes(StandardCharsets.UTF_8));
        boolean passwordOk = encoder.matches(candidatePassword == null ? "" : candidatePassword, passwordHash);
        return userOk && passwordOk;
    }

    public String username() {
        return username;
    }
}
