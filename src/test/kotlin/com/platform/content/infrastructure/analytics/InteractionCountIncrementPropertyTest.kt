package com.platform.content.infrastructure.analytics

import com.mongodb.client.result.UpdateResult
import com.platform.content.domain.model.InteractionType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.jqwik.api.*
import net.jqwik.api.lifecycle.BeforeProperty
import org.junit.jupiter.api.Tag
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.util.UUID

/**
 * Property-based test for interaction count increment.
 *
 * Validates: Requirements 6.4
 *
 * Verifies that for any sequence of interaction events (like, share, or comment)
 * for a single article, the final count for each interaction type equals the total
 * number of events of that type in the sequence. This is verified by checking that
 * the adapter issues the correct $inc MongoDB operations for each event.
 */
@Tag("Feature: content-publishing-platform, Property 17: Interaction count increment")
class InteractionCountIncrementPropertyTest {

    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var adapter: MongoEngagementWriteAdapter

    @BeforeProperty
    fun setUp() {
        mongoTemplate = mockk(relaxed = true)
        adapter = MongoEngagementWriteAdapter(mongoTemplate)

        val updateResult = mockk<UpdateResult>()
        every { updateResult.modifiedCount } returns 1
        every {
            mongoTemplate.upsert(any<Query>(), any<Update>(), EngagementDocument::class.java)
        } returns updateResult
    }

    @Provide
    fun interactionSequences(): Arbitrary<List<InteractionType>> =
        Arbitraries.of(*InteractionType.entries.toTypedArray())
            .list()
            .ofMinSize(1)
            .ofMaxSize(50)

    @Provide
    fun interactionTypes(): Arbitrary<InteractionType> =
        Arbitraries.of(*InteractionType.entries.toTypedArray())

    /**
     * Property: For any sequence of interaction events, the number of $inc operations
     * on each field matches the count of that interaction type in the sequence.
     *
     * Validates: Requirements 6.4
     */
    @Property(tries = 100)
    fun finalCountsMatchEventTotalsPerType(
        @ForAll("interactionSequences") events: List<InteractionType>
    ) {
        // Reset mock for each property trial
        val capturedUpdates = mutableListOf<Update>()
        val updateResult = mockk<UpdateResult>()
        every { updateResult.modifiedCount } returns 1
        every {
            mongoTemplate.upsert(any<Query>(), capture(capturedUpdates), EngagementDocument::class.java)
        } returns updateResult

        val articleId = UUID.randomUUID()

        // Act: replay the entire sequence of interaction events
        events.forEach { type ->
            adapter.recordInteraction(articleId, type)
        }

        // Count expected events per type
        val expectedLikes = events.count { it == InteractionType.LIKE }
        val expectedShares = events.count { it == InteractionType.SHARE }
        val expectedComments = events.count { it == InteractionType.COMMENT }

        // Count actual $inc operations per field from captured updates
        val actualLikes = capturedUpdates.count { it.toString().contains("interactions.likes") }
        val actualShares = capturedUpdates.count { it.toString().contains("interactions.shares") }
        val actualComments = capturedUpdates.count { it.toString().contains("interactions.comments") }

        assert(actualLikes == expectedLikes) {
            "Expected $expectedLikes likes increments but got $actualLikes. Events: $events"
        }
        assert(actualShares == expectedShares) {
            "Expected $expectedShares shares increments but got $actualShares. Events: $events"
        }
        assert(actualComments == expectedComments) {
            "Expected $expectedComments comments increments but got $actualComments. Events: $events"
        }

        // Total operations should equal total events
        assert(capturedUpdates.size == events.size) {
            "Expected ${events.size} total upsert calls but got ${capturedUpdates.size}"
        }
    }

    /**
     * Property: Each interaction event increments exactly one field — the field
     * corresponding to its type — and no other interaction field.
     *
     * Validates: Requirements 6.4
     */
    @Property(tries = 100)
    fun eachInteractionIncrementsExactlyOneField(
        @ForAll("interactionTypes") type: InteractionType
    ) {
        val capturedUpdate = slot<Update>()
        val updateResult = mockk<UpdateResult>()
        every { updateResult.modifiedCount } returns 1
        every {
            mongoTemplate.upsert(any<Query>(), capture(capturedUpdate), EngagementDocument::class.java)
        } returns updateResult

        val articleId = UUID.randomUUID()
        adapter.recordInteraction(articleId, type)

        val updateStr = capturedUpdate.captured.toString()

        val expectedField = when (type) {
            InteractionType.LIKE -> "interactions.likes"
            InteractionType.SHARE -> "interactions.shares"
            InteractionType.COMMENT -> "interactions.comments"
        }

        val otherFields = listOf("interactions.likes", "interactions.shares", "interactions.comments")
            .filter { it != expectedField }

        assert(updateStr.contains(expectedField)) {
            "Expected update to contain '$expectedField' for type $type, but got: $updateStr"
        }

        otherFields.forEach { otherField ->
            assert(!updateStr.contains(otherField)) {
                "Update should not contain '$otherField' for type $type, but got: $updateStr"
            }
        }
    }

    /**
     * Property: All interaction events for the same article target the same
     * articleId in the query, ensuring counts accumulate on the correct document.
     *
     * Validates: Requirements 6.4
     */
    @Property(tries = 100)
    fun allEventsTargetCorrectArticleId(
        @ForAll("interactionSequences") events: List<InteractionType>
    ) {
        val capturedQueries = mutableListOf<Query>()
        val updateResult = mockk<UpdateResult>()
        every { updateResult.modifiedCount } returns 1
        every {
            mongoTemplate.upsert(capture(capturedQueries), any<Update>(), EngagementDocument::class.java)
        } returns updateResult

        val articleId = UUID.randomUUID()

        events.forEach { type ->
            adapter.recordInteraction(articleId, type)
        }

        // Every query should target the same articleId
        capturedQueries.forEach { query ->
            val queryStr = query.toString()
            assert(queryStr.contains(articleId.toString())) {
                "Expected query to target article $articleId, but got: $queryStr"
            }
        }

        assert(capturedQueries.size == events.size) {
            "Expected ${events.size} queries but captured ${capturedQueries.size}"
        }
    }
}
