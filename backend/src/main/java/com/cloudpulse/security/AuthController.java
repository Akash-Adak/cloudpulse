package com.cloudpulse.security;

import com.cloudpulse.web.Dto.ApiError;
import com.cloudpulse.web.Dto.LoginRequest;
import com.cloudpulse.web.Dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AdminAuthenticator authenticator;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;

    public AuthController(AdminAuthenticator authenticator, JwtService jwtService, LoginRateLimiter rateLimiter) {
        this.authenticator = authenticator;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String client = http.getRemoteAddr();
        if (rateLimiter.isBlocked(client)) {
            log.warn("Login blocked for {} (too many failures)", client);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiError(Instant.now(), 429, "Too Many Requests",
                            "Too many failed sign-in attempts. Try again in 15 minutes.", null));
        }
        if (!authenticator.authenticate(request.username(), request.password())) {
            rateLimiter.recordFailure(client);
            log.warn("Failed login attempt from {}", client);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiError(Instant.now(), 401, "Unauthorized",
                            "Incorrect username or password.", null));
        }
        rateLimiter.reset(client);
        LoginResponse response = jwtService.issue(authenticator.username());
        return ResponseEntity.ok(response);
    }
}
