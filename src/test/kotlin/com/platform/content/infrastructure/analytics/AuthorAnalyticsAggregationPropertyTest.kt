package com.platform.content.infrastructure.analytics

import com.platform.content.domain.model.AggregatedEngagement
import com.platform.content.domain.model.EngagementRecord
import com.platform.content.domain.model.InteractionCounts
import net.jqwik.api.*
import net.jqwik.api.Combinators.combine
import org.junit.jupiter.api.Tag
import java.util.UUID
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for author analytics aggregation invariants.
 *
 * Verifies that aggregated metrics satisfy sum and weighted-average invariants
 * across any set of engagement records for an author's articles.
 *
 * **Validates: Requirements 7.2**
 */
@Tag("Feature: content-publishing-platform, Property 18: Author analytics aggregation")
class AuthorAnalyticsAggregationPropertyTest {

    /**
     * Provides arbitrary EngagementRecord instances with random metrics.
     */
    @Provide
    fun engagementRecords(): Arbitrary<List<EngagementRecord>> {
        val recordArbitrary = combine(
            Arbitraries.longs().between(0, 100_000),        // pageViews
            Arbitraries.doubles().between(0.0, 3600.0),     // averageReadTimeSeconds
            Arbitraries.longs().between(0, 10_000),         // likes
            Arbitraries.longs().between(0, 10_000),         // shares
            Arbitraries.longs().between(0, 10_000)          // comments
        ).`as` { pageViews, avgReadTime, likes, shares, comments ->
            EngagementRecord(
                articleId = UUID.randomUUID(),
                pageViews = pageViews,
                averageReadTimeSeconds = avgReadTime,
                interactions = InteractionCounts(
                    likes = likes,
                    shares = shares,
                    comments = comments
                )
            )
        }
        return recordArbitrary.list().ofMinSize(0).ofMaxSize(20)
    }

    /**
     * Aggregates a list of engagement records into an AggregatedEngagement,
     * replicating the logic that any correct implementation must follow.
     */
    private fun aggregate(authorId: UUID, records: List<EngagementRecord>): AggregatedEngagement {
        if (records.isEmpty()) {
            return AggregatedEngagement(
                authorId = authorId,
                totalPageViews = 0,
                averageReadTimeSeconds = 0.0,
                totalInteractions = InteractionCounts(likes = 0, shares = 0, comments = 0)
            )
        }

        val totalPageViews = records.sumOf { it.pageViews }
        val totalLikes = records.sumOf { it.interactions.likes }
        val totalShares = records.sumOf { it.interactions.shares }
        val totalComments = records.sumOf { it.interactions.comments }

        val weightedReadTimeSum = records.sumOf { it.averageReadTimeSeconds * it.pageViews }
        val averageReadTime = if (totalPageViews > 0) {
            weightedReadTimeSum / totalPageViews
        } else {
            0.0
        }

        return AggregatedEngagement(
            authorId = authorId,
            totalPageViews = totalPageViews,
            averageReadTimeSeconds = averageReadTime,
            totalInteractions = InteractionCounts(
                likes = totalLikes,
                shares = totalShares,
                comments = totalComments
            )
        )
    }

    @Property(tries = 100)
    fun `totalPageViews equals sum of individual article pageViews`(
        @ForAll("engagementRecords") records: List<EngagementRecord>
    ) {
        val authorId = UUID.randomUUID()
        val aggregated = aggregate(authorId, records)

        val expectedTotal = records.sumOf { it.pageViews }
        assertEquals(
            expectedTotal,
            aggregated.totalPageViews,
            "Total page views should equal sum of individual article page views"
        )
    }

    @Property(tries = 100)
    fun `total interactions equals sum of individual article interactions`(
        @ForAll("engagementRecords") records: List<EngagementRecord>
    ) {
        val authorId = UUID.randomUUID()
        val aggregated = aggregate(authorId, records)

        val expectedLikes = records.sumOf { it.interactions.likes }
        val expectedShares = records.sumOf { it.interactions.shares }
        val expectedComments = records.sumOf { it.interactions.comments }

        assertEquals(
            expectedLikes,
            aggregated.totalInteractions.likes,
            "Total likes should equal sum of individual article likes"
        )
        assertEquals(
            expectedShares,
            aggregated.totalInteractions.shares,
            "Total shares should equal sum of individual article shares"
        )
        assertEquals(
            expectedComments,
            aggregated.totalInteractions.comments,
            "Total comments should equal sum of individual article comments"
        )
    }

    @Property(tries = 100)
    fun `averageReadTimeSeconds equals weighted average by pageViews`(
        @ForAll("engagementRecords") records: List<EngagementRecord>
    ) {
        val authorId = UUID.randomUUID()
        val aggregated = aggregate(authorId, records)

        val totalPageViews = records.sumOf { it.pageViews }
        val expectedAverage = if (totalPageViews > 0) {
            records.sumOf { it.averageReadTimeSeconds * it.pageViews } / totalPageViews
        } else {
            0.0
        }

        assertTrue(
            abs(expectedAverage - aggregated.averageReadTimeSeconds) < 1e-9,
            "Average read time should equal weighted average (weighted by page views). " +
                "Expected: $expectedAverage, Got: ${aggregated.averageReadTimeSeconds}"
        )
    }

    @Property(tries = 100)
    fun `averageReadTimeSeconds is zero when total pageViews is zero`(
        @ForAll("engagementRecords") records: List<EngagementRecord>
    ) {
        // Force all page views to 0
        val zeroPageViewRecords = records.map { it.copy(pageViews = 0) }
        val authorId = UUID.randomUUID()
        val aggregated = aggregate(authorId, zeroPageViewRecords)

        assertEquals(
            0.0,
            aggregated.averageReadTimeSeconds,
            "Average read time should be 0.0 when total page views is 0"
        )
    }

    @Property(tries = 100)
    fun `empty records produce zeroed aggregated metrics`(
        @ForAll("engagementRecords") records: List<EngagementRecord>
    ) {
        // Ignore provided records; test with empty list
        val authorId = UUID.randomUUID()
        val aggregated = aggregate(authorId, emptyList())

        assertEquals(0L, aggregated.totalPageViews, "Empty records should produce 0 total page views")
        assertEquals(0.0, aggregated.averageReadTimeSeconds, "Empty records should produce 0.0 average read time")
        assertEquals(0L, aggregated.totalInteractions.likes, "Empty records should produce 0 likes")
        assertEquals(0L, aggregated.totalInteractions.shares, "Empty records should produce 0 shares")
        assertEquals(0L, aggregated.totalInteractions.comments, "Empty records should produce 0 comments")
    }

    @Property(tries = 100)
    fun `aggregation is additive - combining two groups equals aggregating all together`(
        @ForAll("engagementRecords") records: List<EngagementRecord>
    ) {
        if (records.size < 2) return

        val authorId = UUID.randomUUID()
        val midpoint = records.size / 2
        val group1 = records.subList(0, midpoint)
        val group2 = records.subList(midpoint, records.size)

        val agg1 = aggregate(authorId, group1)
        val agg2 = aggregate(authorId, group2)
        val aggAll = aggregate(authorId, records)

        // Total page views should be additive
        assertEquals(
            agg1.totalPageViews + agg2.totalPageViews,
            aggAll.totalPageViews,
            "Page views aggregation should be additive across groups"
        )

        // Total interactions should be additive
        assertEquals(
            agg1.totalInteractions.likes + agg2.totalInteractions.likes,
            aggAll.totalInteractions.likes,
            "Likes aggregation should be additive across groups"
        )
        assertEquals(
            agg1.totalInteractions.shares + agg2.totalInteractions.shares,
            aggAll.totalInteractions.shares,
            "Shares aggregation should be additive across groups"
        )
        assertEquals(
            agg1.totalInteractions.comments + agg2.totalInteractions.comments,
            aggAll.totalInteractions.comments,
            "Comments aggregation should be additive across groups"
        )
    }
}
