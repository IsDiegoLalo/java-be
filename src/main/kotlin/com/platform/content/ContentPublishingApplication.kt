package com.platform.content

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Main entry point for the Content Publishing Platform.
 *
 * This microservice provides content management and publishing workflow capabilities
 * including author management, article lifecycle, full-text search, engagement analytics,
 * and event-driven notifications.
 */
@SpringBootApplication
class ContentPublishingApplication

fun main(args: Array<String>) {
    runApplication<ContentPublishingApplication>(*args)
}
