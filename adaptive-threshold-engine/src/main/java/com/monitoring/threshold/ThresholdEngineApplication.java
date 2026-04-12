package com.monitoring.threshold;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.monitoring.threshold.config.ThresholdEngineProperties;

@SpringBootApplication
@EnableConfigurationProperties(ThresholdEngineProperties.class)
public class ThresholdEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThresholdEngineApplication.class, args);
    }
}
