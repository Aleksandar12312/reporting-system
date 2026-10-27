package com.example.monitor.mock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches the whole generator on only when mock.enabled=true
 * (set in application-dev.yml, so it never runs outside the dev profile).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(MockProperties.class)
@ConditionalOnProperty(prefix = "mock", name = "enabled", havingValue = "true")
public class MockConfig {
}
