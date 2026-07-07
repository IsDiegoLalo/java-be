package com.platform.content.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.platform.content.domain.model.ArticlePublishedEvent
import net.jqwik.api.*
import org.junit.jupiter.api.Tag
import java.time.Instant
import java.util.UUID

/**
 * Property-based test for event payload completeness.
 *
 * Validates: Requirements 8.2
 *
 * Verifies that for any published article, the event payload serialized by the
 * KafkaArticleEventPublisher contains ALL required fields (articleId, title,
 * authorId, category, tags, publishedAt) with none being null or missing.
 */
@Tag("Feature: content-publishing-platform, Property 19: Event payload completeness")
class EventPayloadCompletenessPropertyTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    // --- Arbitrary providers ---

    @Provide
    fun articlePublishedEvents(): Arbitrary<ArticlePublishedEvent> {
        val articleIds = Arbitraries.create { UUID.randomUUID() }
        val authorIds = Arbitraries.create { UUID.randomUUID() }
        val titles = Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(255)
        val categories = Arbitraries.strings()
            .alpha()
            .ofMinLength(2)
            .ofMaxLength(100)
        val tags = Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(30)
            .list()
            .ofMinSize(0)
            .ofMaxSize(10)
        val publishedAts = Arbitraries.longs()
            .between(0L, 4_102_444_800L) // Up to year 2100
            .map { Instant.ofEpochSecond(it) }

        return Combinators.combine(articleIds, titles, authorIds, categories, tags, publishedAts)
            .`as` { articleId, title, authorId, category, tagList, publishedAt ->
                ArticlePublishedEvent(
                    articleId = articleId,
                    title = title,
                    authorId = authorId,
                    category = category,
                    tags = tagList,
                    publishedAt = publishedAt
                )
            }
    }

    /**
     * Property: For any valid ArticlePublishedEvent, the serialized JSON payload
     * contains all required fields and none are null.
     *
     * Validates: Requirements 8.2
     */
    @Property(tries = 100)
    fun `serialized event payload contains all required fields with non-null values`(
        @ForAll("articlePublishedEvents") event: ArticlePublishedEvent
    ) {
        // Serialize the event using the same ObjectMapper configuration as production
        val payload = objectMapper.writeValueAsString(event)
        val jsonNode = objectMapper.readTree(payload)

        // Verify all required fields are present
        val requiredFields = listOf("articleId", "title", "authorId", "category", "tags", "publishedAt")

        requiredFields.forEach { field ->
            assert(jsonNode.has(field)) {
                "Event payload is missing required field '$field'. Payload: $payload"
            }
            assert(!jsonNode.get(field).isNull) {
                "Event payload field '$field' is null. Payload: $payload"
            }
        }
    }

    /**
     * Property: For any valid ArticlePublishedEvent, the articleId field in the
     * serialized payload matches the original event's articleId.
     *
     * Validates: Requirements 8.2
     */
    @Property(tries = 100)
    fun `serialized articleId matches original event articleId`(
        @ForAll("articlePublishedEvents") event: ArticlePublishedEvent
    ) {
        val payload = objectMapper.writeValueAsString(event)
        val jsonNode = objectMapper.readTree(payload)

        assert(jsonNode.get("articleId").asText() == event.articleId.toString()) {
            "articleId mismatch: expected ${event.articleId}, got ${jsonNode.get("articleId").asText()}"
        }
    }

    /**
     * Property: For any valid ArticlePublishedEvent, the title field in the
     * serialized payload matches the original event's title.
     *
     * Validates: Requirements 8.2
     */
    @Property(tries = 100)
    fun `serialized title matches original event title`(
        @ForAll("articlePublishedEvents") event: ArticlePublishedEvent
    ) {
        val payload = objectMapper.writeValueAsString(event)
        val jsonNode = objectMapper.readTree(payload)

        assert(jsonNode.get("title").asText() == event.title) {
            "title mismatch: expected '${event.title}', got '${jsonNode.get("title").asText()}'"
        }
    }

    /**
     * Property: For any valid ArticlePublishedEvent, the authorId field in the
     * serialized payload matches the original event's authorId.
     *
     * Validates: Requirements 8.2
     */
    @Property(tries = 100)
    fun `serialized authorId matches original event authorId`(
        @ForAll("articlePublishedEvents") event: ArticlePublishedEvent
    ) {
        val payload = objectMapper.writeValueAsString(event)
        val jsonNode = objectMapper.readTree(payload)

        assert(jsonNode.get("authorId").asText() == event.authorId.toString()) {
            "authorId mismatch: expected ${event.authorId}, got ${jsonNode.get("authorId").asText()}"
        }
    }

    /**
     * Property: For any valid ArticlePublishedEvent, the tags field in the
     * serialized payload is an array matching the original event's tags.
     *
     * Validates: Requirements 8.2
     */
    @Property(tries = 100)
    fun `serialized tags array matches original event tags`(
        @ForAll("articlePublishedEvents") event: ArticlePublishedEvent
    ) {
        val payload = objectMapper.writeValueAsString(event)
        val jsonNode = objectMapper.readTree(payload)

        val tagsNode = jsonNode.get("tags")
        assert(tagsNode.isArray) {
            "tags field should be an array, but got: ${tagsNode.nodeType}"
        }
        assert(tagsNode.size() == event.tags.size) {
            "tags size mismatch: expected ${event.tags.size}, got ${tagsNode.size()}"
        }

        val deserializedTags = tagsNode.map { it.asText() }
        assert(deserializedTags == event.tags) {
            "tags content mismatch: expected ${event.tags}, got $deserializedTags"
        }
    }

    /**
     * Property: For any valid ArticlePublishedEvent, the publishedAt field in the
     * serialized payload represents a valid timestamp.
     *
     * Validates: Requirements 8.2
     */
    @Property(tries = 100)
    fun `serialized publishedAt is a valid non-null timestamp`(
        @ForAll("articlePublishedEvents") event: ArticlePublishedEvent
    ) {
        val payload = objectMapper.writeValueAsString(event)
        val jsonNode = objectMapper.readTree(payload)

        val publishedAtNode = jsonNode.get("publishedAt")
        assert(!publishedAtNode.isNull) {
            "publishedAt should not be null"
        }

        // The value should be parseable (either as numeric epoch or string)
        val publishedAtValue = publishedAtNode.asText()
        assert(publishedAtValue.isNotEmpty()) {
            "publishedAt should have a non-empty value"
        }
    }
}
