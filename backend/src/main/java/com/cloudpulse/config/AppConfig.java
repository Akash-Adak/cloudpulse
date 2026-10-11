package com.cloudpulse.config;

import com.cloudpulse.net.SafeHttpProbe;
import com.cloudpulse.net.SsrfGuard;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SsrfGuard ssrfGuard(@Value("${cloudpulse.ssrf.allow-private-targets:false}") boolean allowPrivate) {
        if (allowPrivate) {
            log.warn("SSRF protection is RELAXED (cloudpulse.ssrf.allow-private-targets=true). "
                    + "Private and loopback targets are allowed. Use for local development only.");
        }
        return new SsrfGuard(allowPrivate);
    }

    @Bean
    public SafeHttpProbe safeHttpProbe(SsrfGuard guard,
                                       @Value("${cloudpulse.probe.max-redirects:3}") int maxRedirects) {
        return new SafeHttpProbe(guard, maxRedirects);
    }
}
