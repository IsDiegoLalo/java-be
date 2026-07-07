package com.platform.content.api.controller

import com.platform.content.api.dto.InteractionRequest
import com.platform.content.api.dto.PageViewRequest
import com.platform.content.api.dto.ReadTimeRequest
import com.platform.content.application.analytics.EngagementService
import com.platform.content.domain.model.InteractionType
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for ingesting engagement analytics events (SRP).
 * Handles write operations only — metric retrieval is in AnalyticsController.
 * All events are processed asynchronously, hence 202 Accepted responses.
 * Delegates processing to EngagementService (DIP).
 */
@RestController
@RequestMapping("/analytics/events")
class AnalyticsEventController(
    private val engagementService: EngagementService
) {

    @PostMapping("/page-view")
    fun recordPageView(@Valid @RequestBody request: PageViewRequest): ResponseEntity<Void> {
        engagementService.recordPageView(request.articleId)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    @PostMapping("/read-time")
    fun recordReadTime(@Valid @RequestBody request: ReadTimeRequest): ResponseEntity<Void> {
        engagementService.recordReadTime(request.articleId, request.seconds)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    @PostMapping("/interaction")
    fun recordInteraction(@Valid @RequestBody request: InteractionRequest): ResponseEntity<Void> {
        engagementService.recordInteraction(request.articleId, InteractionType.valueOf(request.type))
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }
}
