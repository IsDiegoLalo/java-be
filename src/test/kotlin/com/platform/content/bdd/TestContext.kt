package com.platform.content.bdd

import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Shared mutable test context for Cucumber step definitions.
 * Holds state (created entity IDs, last response) across steps within a scenario.
 *
 * This is scenario-scoped: Cucumber creates a fresh Spring context per scenario
 * via its glue lifecycle management.
 */
@Component
class TestContext {

    /** Stores entity IDs by name for cross-step referencing. */
    val authorIds: MutableMap<String, UUID> = mutableMapOf()
    val categoryIds: MutableMap<String, UUID> = mutableMapOf()
    val articleIds: MutableMap<String, UUID> = mutableMapOf()

    /** Last HTTP response for assertion steps. */
    var lastResponseStatus: Int = 0
    var lastResponseBody: String? = null

    /** Event capture for Kafka event assertions. */
    var lastPublishedEvent: Map<String, Any?>? = null

    fun reset() {
        authorIds.clear()
        categoryIds.clear()
        articleIds.clear()
        lastResponseStatus = 0
        lastResponseBody = null
        lastPublishedEvent = null
    }
}
