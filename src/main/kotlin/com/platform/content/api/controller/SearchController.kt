package com.platform.content.api.controller

import com.platform.content.api.dto.ArticleResponse
import com.platform.content.api.dto.PageResponse
import com.platform.content.application.search.SearchService
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for full-text article search (SRP).
 * Separated from ArticleController to keep search as a distinct concern.
 * Delegates validation and search logic to SearchService (DIP).
 */
@RestController
@RequestMapping("/articles/search")
class SearchController(
    private val searchService: SearchService
) {

    @GetMapping
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PageResponse<ArticleResponse>> {
        val pageable = PageRequest.of(page, size)
        val results = searchService.search(q, pageable)
        val response = PageResponse.fromPage(results) { ArticleResponse.fromDomain(it) }
        return ResponseEntity.ok(response)
    }
}
