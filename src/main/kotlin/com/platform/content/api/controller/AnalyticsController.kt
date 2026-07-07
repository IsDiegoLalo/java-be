package com.platform.content.api.controller

import com.platform.content.api.dto.AggregatedEngagementResponse
import com.platform.content.api.dto.EngagementResponse
import com.platform.content.application.analytics.EngagementService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for retrieving engagement analytics metrics (SRP).
 * Handles read operations only — event ingestion is in AnalyticsEventController.
 * Delegates all business logic to EngagementService (DIP).
 */
@RestController
@RequestMapping("/analytics")
class AnalyticsController(
    private val engagementService: EngagementService
) {

    @GetMapping("/articles/{id}")
    fun getArticleMetrics(@PathVariable id: UUID): ResponseEntity<EngagementResponse> {
        val engagement = engagementService.getArticleEngagement(id)
        return ResponseEntity.ok(EngagementResponse.fromDomain(engagement))
    }

    @GetMapping("/authors/{id}")
    fun getAuthorMetrics(@PathVariable id: UUID): ResponseEntity<AggregatedEngagementResponse> {
        val aggregated = engagementService.getAuthorEngagement(id)
        return ResponseEntity.ok(AggregatedEngagementResponse.fromDomain(aggregated))
    }
}
