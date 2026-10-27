package com.example.monitor.api;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.example.monitor.stats.StatsProperties;

/**
 * Registers the settings the REST API needs. StatsProperties is registered here too (it is the same bean),
 * so the API also starts when stats.enabled=false and the statistics job is not created.
 */
@Configuration
@EnableConfigurationProperties({RankingProperties.class, StatsProperties.class})
public class ApiConfig {
}
