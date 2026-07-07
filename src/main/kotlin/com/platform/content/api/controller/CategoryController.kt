package com.platform.content.api.controller

import com.platform.content.api.dto.CategoryResponse
import com.platform.content.api.dto.CreateCategoryRequest
import com.platform.content.api.dto.UpdateCategoryRequest
import com.platform.content.application.category.CategoryService
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
 * REST controller for Category CRUD operations.
 * Delegates all business logic to CategoryService (SRP).
 */
@RestController
@RequestMapping("/categories")
class CategoryController(
    private val categoryService: CategoryService
) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreateCategoryRequest): ResponseEntity<CategoryResponse> {
        val category = categoryService.create(request.name, request.description)
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.fromDomain(category))
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<CategoryResponse> {
        val category = categoryService.findById(id)
        return ResponseEntity.ok(CategoryResponse.fromDomain(category))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateCategoryRequest
    ): ResponseEntity<CategoryResponse> {
        val category = categoryService.update(id, request.name, request.description)
        return ResponseEntity.ok(CategoryResponse.fromDomain(category))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        categoryService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
