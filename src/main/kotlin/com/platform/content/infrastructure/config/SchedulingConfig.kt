package com.platform.content.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Enables Spring's scheduled task execution capability.
 * Used by the OutboxProcessor to periodically retry pending events
 * that were not delivered to Kafka on the initial attempt.
 */
@Configuration
@EnableScheduling
class SchedulingConfig
