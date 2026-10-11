package com.cloudpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CloudPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudPulseApplication.class, args);
    }
}
