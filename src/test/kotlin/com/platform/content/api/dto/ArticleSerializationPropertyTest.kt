package com.platform.content.api.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import net.jqwik.api.*
import net.jqwik.api.lifecycle.BeforeProperty
import org.junit.jupiter.api.Tag
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Property-based test for Article serialization round-trip.
 *
 * Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5
 *
 * Verifies that for any valid Article entity, serializing to JSON via ArticleResponse
 * and deserializing back produces a field-by-field equivalent object where:
 * - Text fields match by string equality
 * - Tags match by element-order equality
 * - Date-time fields match by millisecond-precision equality
 * - Null fields appear as JSON null (not omitted)
 * - Empty tags appear as an empty JSON array (not null)
 * - All date-time fields use ISO 8601 format
 */
@Tag("Feature: content-publishing-platform, Property 20: Article serialization round-trip")
class ArticleSerializationPropertyTest {

    private lateinit var objectMapper: ObjectMapper

    @BeforeProperty
    fun setUp() {
        objectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
    }

    @Provide
    fun arbitraryArticles(): Arbitrary<Article> {
        val uuids = Arbitraries.create { UUID.randomUUID() }
        val titles = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
        val bodies = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100)
        val summaries = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
            .injectNull(0.3)
        val statuses = Arbitraries.of(ArticleStatus.Draft, ArticleStatus.Review, ArticleStatus.Published)
        val tags = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
            .list().ofMinSize(0).ofMaxSize(5)
        // Generate instants truncated to milliseconds to ensure round-trip precision
        val instants = Arbitraries.longs()
            .between(0L, 4_102_444_800_000L) // Up to ~2100
            .map { millis -> Instant.ofEpochMilli(millis) }

        // Split into two combines since jqwik supports max 8 parameters per combine
        return Combinators.combine(uuids, titles, bodies, summaries, uuids, uuids, tags, statuses)
            .flatAs { id, title, body, summary, authorId, categoryId, tagList, status ->
                Combinators.combine(instants, instants, instants)
                    .`as` { createdAt, updatedAt, publishedAtRaw ->
                        val publishedAt = if (status == ArticleStatus.Published) publishedAtRaw else null
                        Article(
                            id = id,
                            title = title,
                            body = body,
                            summary = summary,
                            authorId = authorId,
                            categoryId = categoryId,
                            tags = tagList,
                            status = status,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                            publishedAt = publishedAt
                        )
                    }
            }
    }

    /**
     * Property: Serializing an ArticleResponse to JSON and deserializing back
     * produces a field-by-field equivalent object.
     */
    @Property(tries = 100)
    fun articleSerializationRoundTripPreservesAllFields(
        @ForAll("arbitraryArticles") article: Article
    ) {
        val response = ArticleResponse.fromDomain(article)
        val json = objectMapper.writeValueAsString(response)
        val deserialized: ArticleResponse = objectMapper.readValue(json)

        assert(response.id == deserialized.id) {
            "ID mismatch: expected ${response.id}, got ${deserialized.id}"
        }
        assert(response.title == deserialized.title) {
            "Title mismatch: expected '${response.title}', got '${deserialized.title}'"
        }
        assert(response.body == deserialized.body) {
            "Body mismatch: expected '${response.body}', got '${deserialized.body}'"
        }
        assert(response.summary == deserialized.summary) {
            "Summary mismatch: expected '${response.summary}', got '${deserialized.summary}'"
        }
        assert(response.authorId == deserialized.authorId) {
            "AuthorId mismatch: expected ${response.authorId}, got ${deserialized.authorId}"
        }
        assert(response.categoryId == deserialized.categoryId) {
            "CategoryId mismatch: expected ${response.categoryId}, got ${deserialized.categoryId}"
        }
        assert(response.tags == deserialized.tags) {
            "Tags mismatch: expected ${response.tags}, got ${deserialized.tags}"
        }
        assert(response.status == deserialized.status) {
            "Status mismatch: expected '${response.status}', got '${deserialized.status}'"
        }
        assert(response.createdAt == deserialized.createdAt) {
            "CreatedAt mismatch: expected '${response.createdAt}', got '${deserialized.createdAt}'"
        }
        assert(response.updatedAt == deserialized.updatedAt) {
            "UpdatedAt mismatch: expected '${response.updatedAt}', got '${deserialized.updatedAt}'"
        }
        assert(response.publishedAt == deserialized.publishedAt) {
            "PublishedAt mismatch: expected '${response.publishedAt}', got '${deserialized.publishedAt}'"
        }
    }

    /**
     * Property: Null fields (summary, publishedAt) appear as JSON null, not omitted.
     */
    @Property(tries = 100)
    fun nullFieldsAppearAsJsonNull(
        @ForAll("arbitraryArticles") article: Article
    ) {
        val response = ArticleResponse.fromDomain(article)
        val json = objectMapper.writeValueAsString(response)
        val tree = objectMapper.readTree(json)

        // summary field must always be present in JSON
        assert(tree.has("summary")) {
            "JSON must contain 'summary' field, even when null. JSON: $json"
        }
        if (response.summary == null) {
            assert(tree.get("summary").isNull) {
                "When summary is null, JSON must contain explicit null. Got: ${tree.get("summary")}"
            }
        }

        // publishedAt field must always be present in JSON
        assert(tree.has("publishedAt")) {
            "JSON must contain 'publishedAt' field, even when null. JSON: $json"
        }
        if (response.publishedAt == null) {
            assert(tree.get("publishedAt").isNull) {
                "When publishedAt is null, JSON must contain explicit null. Got: ${tree.get("publishedAt")}"
            }
        }
    }

    /**
     * Property: Empty tags list appears as an empty JSON array, not null.
     */
    @Property(tries = 100)
    fun emptyTagsAppearAsEmptyJsonArray(
        @ForAll("arbitraryArticles") article: Article
    ) {
        val response = ArticleResponse.fromDomain(article)
        val json = objectMapper.writeValueAsString(response)
        val tree = objectMapper.readTree(json)

        val tagsNode = tree.get("tags")
        assert(tagsNode != null && tagsNode.isArray) {
            "Tags must always be a JSON array. Got: $tagsNode"
        }
        if (response.tags.isEmpty()) {
            assert(tagsNode.size() == 0) {
                "Empty tags must serialize as empty array []. Got: $tagsNode"
            }
        }
    }

    /**
     * Property: All date-time fields use ISO 8601 format (yyyy-MM-dd'T'HH:mm:ss.SSS'Z').
     */
    @Property(tries = 100)
    fun dateTimeFieldsUseIso8601Format(
        @ForAll("arbitraryArticles") article: Article
    ) {
        val response = ArticleResponse.fromDomain(article)
        val iso8601Pattern = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""")

        assert(iso8601Pattern.matches(response.createdAt)) {
            "createdAt '${response.createdAt}' does not match ISO 8601 pattern yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        }
        assert(iso8601Pattern.matches(response.updatedAt)) {
            "updatedAt '${response.updatedAt}' does not match ISO 8601 pattern yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        }
        if (response.publishedAt != null) {
            assert(iso8601Pattern.matches(response.publishedAt!!)) {
                "publishedAt '${response.publishedAt}' does not match ISO 8601 pattern yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
            }
        }
    }

    /**
     * Property: Date-time fields match by millisecond-precision equality after round-trip.
     * The original Instant (truncated to millis) should be recoverable from the serialized string.
     */
    @Property(tries = 100)
    fun dateTimeFieldsPreserveMillisecondPrecision(
        @ForAll("arbitraryArticles") article: Article
    ) {
        val response = ArticleResponse.fromDomain(article)

        // Parse the formatted dates back to Instant and compare with original (truncated to millis)
        val parsedCreatedAt = Instant.parse(response.createdAt)
        val parsedUpdatedAt = Instant.parse(response.updatedAt)

        val expectedCreatedAt = article.createdAt.truncatedTo(ChronoUnit.MILLIS)
        val expectedUpdatedAt = article.updatedAt.truncatedTo(ChronoUnit.MILLIS)

        assert(parsedCreatedAt.equals(expectedCreatedAt)) {
            "createdAt precision loss: original=${article.createdAt}, formatted='${response.createdAt}', parsed=$parsedCreatedAt"
        }
        assert(parsedUpdatedAt.equals(expectedUpdatedAt)) {
            "updatedAt precision loss: original=${article.updatedAt}, formatted='${response.updatedAt}', parsed=$parsedUpdatedAt"
        }

        if (article.publishedAt != null) {
            val parsedPublishedAt = Instant.parse(response.publishedAt!!)
            val expectedPublishedAt = article.publishedAt!!.truncatedTo(ChronoUnit.MILLIS)
            assert(parsedPublishedAt.equals(expectedPublishedAt)) {
                "publishedAt precision loss: original=${article.publishedAt}, formatted='${response.publishedAt}', parsed=$parsedPublishedAt"
            }
        }
    }
}
