package com.platform.content.api.controller

import com.platform.content.api.dto.AuthorResponse
import com.platform.content.api.dto.CreateAuthorRequest
import com.platform.content.api.dto.UpdateAuthorRequest
import com.platform.content.application.author.AuthorService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for Author CRUD operations.
 * Delegates all business logic to AuthorService (SRP).
 */
@RestController
@RequestMapping("/authors")
class AuthorController(
    private val authorService: AuthorService
) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreateAuthorRequest): ResponseEntity<AuthorResponse> {
        val author = authorService.create(request.name, request.email, request.bio)
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthorResponse.fromDomain(author))
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<AuthorResponse> {
        val author = authorService.findById(id)
        return ResponseEntity.ok(AuthorResponse.fromDomain(author))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateAuthorRequest
    ): ResponseEntity<AuthorResponse> {
        val author = authorService.update(id, request.name, request.email, request.bio)
        return ResponseEntity.ok(AuthorResponse.fromDomain(author))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        authorService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
