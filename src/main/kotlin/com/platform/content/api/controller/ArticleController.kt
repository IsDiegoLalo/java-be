package com.platform.content.api.controller

import com.platform.content.api.dto.ArticleResponse
import com.platform.content.api.dto.CreateArticleRequest
import com.platform.content.api.dto.PageResponse
import com.platform.content.api.dto.UpdateArticleRequest
import com.platform.content.application.article.ArticleService
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.port.ArticleFilter
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for Article CRUD operations.
 * Delegates all business logic to ArticleService (SRP).
 */
@RestController
@RequestMapping("/articles")
class ArticleController(
    private val articleService: ArticleService
) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreateArticleRequest): ResponseEntity<ArticleResponse> {
        val article = articleService.create(
            title = request.title,
            body = request.body,
            summary = request.summary,
            authorId = request.authorId,
            categoryId = request.categoryId,
            tags = request.tags
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ArticleResponse.fromDomain(article))
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<ArticleResponse> {
        val article = articleService.findById(id)
        return ResponseEntity.ok(ArticleResponse.fromDomain(article))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateArticleRequest
    ): ResponseEntity<ArticleResponse> {
        val article = articleService.update(
            id = id,
            title = request.title,
            body = request.body,
            summary = request.summary,
            authorId = request.authorId,
            categoryId = request.categoryId,
            tags = request.tags
        )
        return ResponseEntity.ok(ArticleResponse.fromDomain(article))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        articleService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    fun list(
        @RequestParam(required = false) authorId: UUID?,
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) tags: List<String>?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PageResponse<ArticleResponse>> {
        val filter = ArticleFilter(
            authorId = authorId,
            categoryId = categoryId,
            status = status?.let { ArticleStatus.fromString(it) },
            tags = tags?.takeIf { it.isNotEmpty() }
        )
        val pageable = PageRequest.of(page, size)
        val articlePage = articleService.list(filter, pageable)
        val response = PageResponse.fromPage(articlePage) { ArticleResponse.fromDomain(it) }
        return ResponseEntity.ok(response)
    }
}
