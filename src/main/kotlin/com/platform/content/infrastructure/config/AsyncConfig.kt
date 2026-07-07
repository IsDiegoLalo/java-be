package com.platform.content.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

/**
 * Enables Spring's asynchronous method execution capability.
 * Used by the EngagementService to process analytics write operations
 * asynchronously, ensuring the main API responses are not blocked by
 * analytics recording (requirement 6.5).
 */
@Configuration
@EnableAsync
class AsyncConfig
