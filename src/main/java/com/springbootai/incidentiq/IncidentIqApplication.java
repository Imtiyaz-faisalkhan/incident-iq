package com.springbootai.incidentiq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class IncidentIqApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentIqApplication.class, args);
    }

    /** Single source of "now" (UTC), so time-dependent logic can be tested with a fixed clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}