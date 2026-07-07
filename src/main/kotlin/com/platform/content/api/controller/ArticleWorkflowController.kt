package com.platform.content.api.controller

import com.platform.content.api.dto.ArticleResponse
import com.platform.content.api.dto.TransitionStatusRequest
import com.platform.content.application.article.ArticleWorkflowService
import com.platform.content.domain.model.ArticleStatus
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for article workflow transitions (SRP).
 * Separated from ArticleController to isolate status transition
 * concerns from CRUD operations.
 */
@RestController
@RequestMapping("/articles")
class ArticleWorkflowController(
    private val articleWorkflowService: ArticleWorkflowService
) {

    @PutMapping("/{id}/status")
    fun transitionStatus(
        @PathVariable id: UUID,
        @Valid @RequestBody request: TransitionStatusRequest
    ): ResponseEntity<ArticleResponse> {
        val targetStatus = ArticleStatus.fromString(request.targetStatus)
        val article = articleWorkflowService.transitionStatus(id, targetStatus)
        return ResponseEntity.ok(ArticleResponse.fromDomain(article))
    }
}
