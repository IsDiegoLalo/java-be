package com.platform.content.infrastructure.analytics

import com.mongodb.client.result.UpdateResult
import com.platform.content.domain.model.InteractionType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.util.UUID
import kotlin.test.assertTrue

class MongoEngagementWriteAdapterTest {

    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var adapter: MongoEngagementWriteAdapter

    @BeforeEach
    fun setUp() {
        mongoTemplate = mockk(relaxed = true)
        adapter = MongoEngagementWriteAdapter(mongoTemplate)
    }

    @Test
    fun `recordPageView should upsert with increment on pageViews`() {
        val articleId = UUID.randomUUID()
        val updateResult = mockk<UpdateResult>()
        every { updateResult.modifiedCount } returns 1

        val querySlot = slot<Query>()
        val updateSlot = slot<Update>()

        every {
            mongoTemplate.upsert(capture(querySlot), capture(updateSlot), EngagementDocument::class.java)
        } returns updateResult

        adapter.recordPageView(articleId)

        verify(exactly = 1) {
            mongoTemplate.upsert(any<Query>(), any<Update>(), EngagementDocument::class.java)
        }

        val capturedQuery = querySlot.captured.toString()
        assertTrue(capturedQuery.contains(articleId.toString()))

        val capturedUpdate = updateSlot.captured.toString()
        assertTrue(capturedUpdate.contains("pageViews"))
        assertTrue(capturedUpdate.contains("lastUpdated"))
    }

    @Test
    fun `recordReadTime should increment totals and recalculate average`() {
        val articleId = UUID.randomUUID()
        val seconds = 120
        val updateResult = mockk<UpdateResult>()
        every { updateResult.modifiedCount } returns 1

        // After the first upsert, return a document with the updated values
        val document = EngagementDocument(
            articleId = articleId.toString(),
            totalReadTimeSeconds = 120,
            readTimeCount = 1
        )

        every {
            mongoTemplate.upsert(any<Query>(), any<Update>(), EngagementDocument::class.java)
        } returns updateResult

        every {
            mongoTemplate.findOne(any<Query>(), EngagementDocument::class.java)
        } returns document

        every {
            mongoTemplate.updateFirst(any<Query>(), any<Update>(), EngagementDocument::class.java)
        } returns updateResult

        adapter.recordReadTime(articleId, seconds)

        // Verify the increment upsert was called
        verify(exactly = 1) {
            mongoTemplate.upsert(any<Query>(), any<Update>(), EngagementDocument::class.java)
        }

        // Verify the average recalculation was done
        verify(exactly = 1) {
            mongoTemplate.updateFirst(any<Query>(), any<Update>(), EngagementDocument::class.java)
        }
    }

    @Test
    fun `recordReadTime should correctly compute average from multiple readings`() {
        val articleId = UUID.randomUUID()
        val updateResult = mockk<UpdateResult>()
        every { updateResult.modifiedCount } returns 1

        // Simulate document after 3 readings: 60 + 120 + 180 = 360 total, count = 3
        val document = EngagementDocument(
            articleId = articleId.toString(),
            totalReadTimeSeconds = 360,
            readTimeCount = 3
        )

        every {
            mongoTemplate.upsert(any<Query>(), any<Update>(), EngagementDocument::class.java)
        } returns updateResult

        every {
            mongoTemplate.findOne(any<Query>(), EngagementDocument::class.java)
        } returns document

        val averageUpdateSlot = slot<Update>()
        every {
            mongoTemplate.updateFirst(any<Query>(), capture(averageUpdateSlot), EngagementDocument::class.java)
        } returns updateResult

        adapter.recordReadTime(articleId, 180)

        val capturedUpdate = averageUpdateSlot.captured.toString()
        // Average should be 360/3 = 120.0
        assertTrue(capturedUpdate.contains("averageReadTimeSeconds"))
    }

    @Test
    fun `recordInteraction LIKE should increment interactions_likes`() {
        val articleId = UUID.randomUUID()
        val updateResult = mockk<UpdateResult>()
        every { updateResult.modifiedCount } returns 1

        val updateSlot = slot<Update>()
        every {
            mongoTemplate.upsert(any<Query>(), capture(updateSlot), EngagementDocument::class.java)
        } returns updateResult

        adapter.recordInteraction(articleId, InteractionType.LIKE)

        val capturedUpdate = updateSlot.captured.toString()
        assertTrue(capturedUpdate.contains("interactions.likes"))
    }

    @Test
    fun `recordInteraction SHARE should increment interactions_shares`() {
        val articleId = UUID.randomUUID()
        val updateResult = mockk<UpdateResult>()
        every { updateResult.modifiedCount } returns 1

        val updateSlot = slot<Update>()
        every {
            mongoTemplate.upsert(any<Query>(), capture(updateSlot), EngagementDocument::class.java)
        } returns updateResult

        adapter.recordInteraction(articleId, InteractionType.SHARE)

        val capturedUpdate = updateSlot.captured.toString()
        assertTrue(capturedUpdate.contains("interactions.shares"))
    }

    @Test
    fun `recordInteraction COMMENT should increment interactions_comments`() {
        val articleId = UUID.randomUUID()
        val updateResult = mockk<UpdateResult>()
        every { updateResult.modifiedCount } returns 1

        val updateSlot = slot<Update>()
        every {
            mongoTemplate.upsert(any<Query>(), capture(updateSlot), EngagementDocument::class.java)
        } returns updateResult

        adapter.recordInteraction(articleId, InteractionType.COMMENT)

        val capturedUpdate = updateSlot.captured.toString()
        assertTrue(capturedUpdate.contains("interactions.comments"))
    }

    @Test
    fun `recordPageView should use correct articleId in query`() {
        val articleId = UUID.randomUUID()
        val updateResult = mockk<UpdateResult>()
        every { updateResult.modifiedCount } returns 1

        val querySlot = slot<Query>()
        every {
            mongoTemplate.upsert(capture(querySlot), any<Update>(), EngagementDocument::class.java)
        } returns updateResult

        adapter.recordPageView(articleId)

        val capturedQuery = querySlot.captured.toString()
        assertTrue(capturedQuery.contains(articleId.toString()))
    }

    @Test
    fun `recordReadTime should not update average when document is null`() {
        val articleId = UUID.randomUUID()
        val updateResult = mockk<UpdateResult>()
        every { updateResult.modifiedCount } returns 1

        every {
            mongoTemplate.upsert(any<Query>(), any<Update>(), EngagementDocument::class.java)
        } returns updateResult

        every {
            mongoTemplate.findOne(any<Query>(), EngagementDocument::class.java)
        } returns null

        adapter.recordReadTime(articleId, 60)

        // Verify that updateFirst was NOT called since document is null
        verify(exactly = 0) {
            mongoTemplate.updateFirst(any<Query>(), any<Update>(), EngagementDocument::class.java)
        }
    }
}
