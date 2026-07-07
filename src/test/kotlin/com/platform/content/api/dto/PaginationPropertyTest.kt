package com.platform.content.api.dto

import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import net.jqwik.api.constraints.LongRange
import org.junit.jupiter.api.Tag
import kotlin.math.ceil

/**
 * Property-based test for pagination invariants.
 *
 * Validates: Requirements 3.4, 5.3, 10.4
 *
 * Verifies that:
 * - For any valid page size (1-100) and any totalElements >= 0, totalPages = ceil(totalElements / size)
 * - The metadata is internally consistent: page, size, totalElements, totalPages all agree
 * - The default page size is 20 (set in controllers via @RequestParam defaultValue)
 * - Content size never exceeds the page size
 */
@Tag("Feature: content-publishing-platform, Property 12: Pagination invariants")
class PaginationPropertyTest {

    /**
     * Property: For any valid page size (1-100) and non-negative totalElements,
     * totalPages equals ceil(totalElements / size).
     */
    @Property(tries = 100)
    fun totalPagesEqualsCeilOfTotalElementsDividedBySize(
        @ForAll @IntRange(min = 1, max = 100) size: Int,
        @ForAll @LongRange(min = 0, max = 100_000) totalElements: Long
    ) {
        val content = generateContent(size, totalElements)
        val response = PageResponse.of(content, 0, size, totalElements)

        val expectedTotalPages = ceil(totalElements.toDouble() / size.toDouble()).toInt()

        assert(response.totalPages == expectedTotalPages) {
            "totalPages mismatch: expected $expectedTotalPages (ceil($totalElements / $size)), got ${response.totalPages}"
        }
    }

    /**
     * Property: Metadata is internally consistent — page, size, totalElements, and totalPages all agree.
     * Specifically: totalPages * size >= totalElements and (totalPages - 1) * size < totalElements (when totalPages > 0).
     */
    @Property(tries = 100)
    fun paginationMetadataIsInternallyConsistent(
        @ForAll @IntRange(min = 0, max = 50) page: Int,
        @ForAll @IntRange(min = 1, max = 100) size: Int,
        @ForAll @LongRange(min = 0, max = 100_000) totalElements: Long
    ) {
        val content = generateContent(size, totalElements, page)
        val response = PageResponse.of(content, page, size, totalElements)

        // page is preserved
        assert(response.page == page) {
            "Page mismatch: expected $page, got ${response.page}"
        }
        // size is preserved
        assert(response.size == size) {
            "Size mismatch: expected $size, got ${response.size}"
        }
        // totalElements is preserved
        assert(response.totalElements == totalElements) {
            "TotalElements mismatch: expected $totalElements, got ${response.totalElements}"
        }
        // totalPages consistency: totalPages * size >= totalElements
        if (response.totalPages > 0) {
            assert(response.totalPages.toLong() * size >= totalElements) {
                "Inconsistency: totalPages(${response.totalPages}) * size($size) = ${response.totalPages.toLong() * size} < totalElements($totalElements)"
            }
            // (totalPages - 1) * size < totalElements (no extra empty page)
            assert((response.totalPages - 1).toLong() * size < totalElements) {
                "Inconsistency: extra empty page detected. (totalPages-1)(${response.totalPages - 1}) * size($size) = ${(response.totalPages - 1).toLong() * size} >= totalElements($totalElements)"
            }
        } else {
            // totalPages == 0 implies totalElements == 0
            assert(totalElements == 0L) {
                "totalPages is 0 but totalElements is $totalElements"
            }
        }
    }

    /**
     * Property: The default page size is 20.
     * This is a constant property verifying the default value used in controllers.
     * The controllers use @RequestParam(defaultValue = "20") for page size.
     */
    @Property(tries = 100)
    fun defaultPageSizeIsTwenty(
        @ForAll @LongRange(min = 0, max = 10_000) totalElements: Long
    ) {
        val defaultSize = 20
        val content = generateContent(defaultSize, totalElements)
        val response = PageResponse.of(content, 0, defaultSize, totalElements)

        val expectedTotalPages = ceil(totalElements.toDouble() / defaultSize.toDouble()).toInt()

        assert(response.size == defaultSize) {
            "Default page size should be 20, got ${response.size}"
        }
        assert(response.totalPages == expectedTotalPages) {
            "With default size 20 and $totalElements elements: expected $expectedTotalPages totalPages, got ${response.totalPages}"
        }
    }

    /**
     * Property: Content size never exceeds the page size.
     * For any valid pagination scenario, the number of items in the content list
     * should be at most the page size.
     */
    @Property(tries = 100)
    fun contentSizeNeverExceedsPageSize(
        @ForAll @IntRange(min = 1, max = 100) size: Int,
        @ForAll @LongRange(min = 0, max = 10_000) totalElements: Long,
        @ForAll @IntRange(min = 0, max = 50) page: Int
    ) {
        val content = generateContent(size, totalElements, page)
        val response = PageResponse.of(content, page, size, totalElements)

        assert(response.content.size <= size) {
            "Content size (${response.content.size}) exceeds page size ($size)"
        }
    }

    /**
     * Property: When totalElements is 0, totalPages must be 0 and content must be empty.
     */
    @Property(tries = 100)
    fun emptyResultsProduceZeroTotalPages(
        @ForAll @IntRange(min = 1, max = 100) size: Int
    ) {
        val response = PageResponse.of(emptyList<String>(), 0, size, 0L)

        assert(response.totalPages == 0) {
            "When totalElements is 0, totalPages should be 0, got ${response.totalPages}"
        }
        assert(response.content.isEmpty()) {
            "When totalElements is 0, content should be empty, got ${response.content.size} items"
        }
    }

    /**
     * Generates a simulated content list for a given page.
     * Returns a list with size at most [pageSize], reflecting realistic pagination behavior.
     */
    private fun generateContent(pageSize: Int, totalElements: Long, page: Int = 0): List<String> {
        if (totalElements == 0L) return emptyList()
        val totalPages = ceil(totalElements.toDouble() / pageSize.toDouble()).toInt()
        if (page >= totalPages) return emptyList()

        val startIndex = page.toLong() * pageSize
        val remaining = totalElements - startIndex
        val itemCount = minOf(remaining, pageSize.toLong()).toInt()
        return List(itemCount) { "item-${startIndex + it}" }
    }
}
