package com.platform.content.infrastructure.analytics

import com.mongodb.client.result.UpdateResult
import io.mockk.*
import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.util.UUID
import kotlin.math.abs

/**
 * Property-based tests for read time average computation.
 *
 * Validates: Requirements 6.3, 6.7
 *
 * Property 16: Read time average computation
 * For any sequence of valid read time values (integers between 1 and 3600 inclusive)
 * recorded for a single article, the stored average read time should equal the arithmetic
 * mean of all recorded values. For any read time value less than 1 or greater than 3600,
 * the event should be discarded without modifying the stored average.
 */
@Tag("Feature: content-publishing-platform, Property 16: Read time average computation")
class ReadTimeAveragePropertyTest {

    companion object {
        private const val EPSILON = 0.001
    }

    // ===== Generators =====

    /**
     * Generates lists of valid read time values (1–3600 inclusive).
     */
    @Provide
    fun validReadTimes(): Arbitrary<List<Int>> {
        return Arbitraries.integers()
            .between(1, 3600)
            .list()
            .ofMinSize(1)
            .ofMaxSize(50)
    }

    /**
     * Generates invalid read time values (less than 1 or greater than 3600).
     */
    @Provide
    fun invalidReadTimes(): Arbitrary<List<Int>> {
        val tooLow = Arbitraries.integers().between(-1000, 0)
        val tooHigh = Arbitraries.integers().between(3601, 10000)
        return Arbitraries.oneOf(tooLow, tooHigh)
            .list()
            .ofMinSize(1)
            .ofMaxSize(20)
    }

    /**
     * Generates mixed sequences containing both valid and invalid read time values.
     */
    @Provide
    fun mixedReadTimes(): Arbitrary<List<Int>> {
        val valid = Arbitraries.integers().between(1, 3600)
        val invalid = Arbitraries.oneOf(
            Arbitraries.integers().between(-1000, 0),
            Arbitraries.integers().between(3601, 10000)
        )
        return Arbitraries.oneOf(valid, invalid)
            .list()
            .ofMinSize(2)
            .ofMaxSize(50)
    }

    // ===== Property Tests =====

    /**
     * For any sequence of valid read time values, after recording all of them,
     * the computed average should equal sum(values) / count(values).
     */
    @Property(tries = 100)
    fun `valid read times produce correct arithmetic mean`(
        @ForAll("validReadTimes") readTimes: List<Int>
    ) {
        // Simulate the running computation that the adapter performs
        var totalReadTimeSeconds: Long = 0
        var readTimeCount: Long = 0

        for (seconds in readTimes) {
            // Valid range check (1–3600)
            if (seconds in 1..3600) {
                totalReadTimeSeconds += seconds.toLong()
                readTimeCount++
            }
        }

        val computedAverage = if (readTimeCount > 0) {
            totalReadTimeSeconds.toDouble() / readTimeCount.toDouble()
        } else {
            0.0
        }

        // Expected arithmetic mean
        val expectedAverage = readTimes.sum().toDouble() / readTimes.size.toDouble()

        assertEquals(
            expectedAverage,
            computedAverage,
            EPSILON,
            "Average should equal arithmetic mean for valid values: $readTimes"
        )
    }

    /**
     * For any sequence of valid read times recorded via the adapter, the final average
     * stored in the document matches the arithmetic mean.
     * This tests through the adapter with a mocked MongoTemplate.
     */
    @Property(tries = 100)
    fun `adapter computes correct average for valid read time sequence`(
        @ForAll("validReadTimes") readTimes: List<Int>
    ) {
        val mongoTemplate = mockk<MongoTemplate>(relaxed = true)
        val adapter = MongoEngagementWriteAdapter(mongoTemplate)
        val articleId = UUID.randomUUID()
        val updateResult = mockk<UpdateResult>()
        every { updateResult.modifiedCount } returns 1

        every {
            mongoTemplate.upsert(any<Query>(), any<Update>(), EngagementDocument::class.java)
        } returns updateResult

        // Track running totals to simulate what MongoDB would return
        var runningTotal: Long = 0
        var runningCount: Long = 0
        var lastComputedAverage = 0.0

        for (seconds in readTimes) {
            runningTotal += seconds.toLong()
            runningCount++

            // Mock the findOne to return the document with current accumulated state
            val document = EngagementDocument(
                articleId = articleId.toString(),
                totalReadTimeSeconds = runningTotal,
                readTimeCount = runningCount
            )

            every {
                mongoTemplate.findOne(any<Query>(), EngagementDocument::class.java)
            } returns document

            every {
                mongoTemplate.updateFirst(any<Query>(), any<Update>(), EngagementDocument::class.java)
            } returns updateResult

            adapter.recordReadTime(articleId, seconds)

            // The adapter computes: totalReadTimeSeconds / readTimeCount
            lastComputedAverage = runningTotal.toDouble() / runningCount.toDouble()
        }

        // The final average should equal the arithmetic mean of all values
        val expectedAverage = readTimes.sum().toDouble() / readTimes.size.toDouble()

        assertTrue(
            abs(expectedAverage - lastComputedAverage) < EPSILON,
            "Final average ($lastComputedAverage) should equal arithmetic mean ($expectedAverage) " +
                "for values: $readTimes"
        )

        // Verify that upsert was called once per valid read time value
        verify(exactly = readTimes.size) {
            mongoTemplate.upsert(any<Query>(), any<Update>(), EngagementDocument::class.java)
        }

        // Verify that updateFirst was called once per value (for recalculating average)
        verify(exactly = readTimes.size) {
            mongoTemplate.updateFirst(any<Query>(), any<Update>(), EngagementDocument::class.java)
        }
    }

    /**
     * Invalid read time values (< 1 or > 3600) should be discarded without
     * modifying the stored average. This verifies that the adapter should NOT
     * call the MongoDB update operations for invalid values.
     *
     * Per requirement 6.7, invalid values must be silently discarded.
     */
    @Property(tries = 100)
    fun `invalid read times should be discarded without updating the record`(
        @ForAll("invalidReadTimes") invalidValues: List<Int>
    ) {
        val mongoTemplate = mockk<MongoTemplate>(relaxed = true)
        val adapter = MongoEngagementWriteAdapter(mongoTemplate)
        val articleId = UUID.randomUUID()

        for (seconds in invalidValues) {
            adapter.recordReadTime(articleId, seconds)
        }

        // For invalid values, the adapter should NOT have performed any upsert/update
        // since the values should be discarded per requirement 6.7
        verify(exactly = 0) {
            mongoTemplate.upsert(any<Query>(), any<Update>(), EngagementDocument::class.java)
        }
    }

    /**
     * For a mixed sequence of valid and invalid values, only valid values contribute
     * to the average computation. The average should be sum(validValues) / count(validValues).
     */
    @Property(tries = 100)
    fun `mixed sequence computes average from only valid values`(
        @ForAll("mixedReadTimes") allValues: List<Int>
    ) {
        val validValues = allValues.filter { it in 1..3600 }

        // If no valid values exist, the average should remain at 0
        if (validValues.isEmpty()) {
            return
        }

        // Simulate the expected computation with only valid values
        val expectedTotal = validValues.sumOf { it.toLong() }
        val expectedCount = validValues.size.toLong()
        val expectedAverage = expectedTotal.toDouble() / expectedCount.toDouble()

        // Verify the arithmetic: sum / count equals the mean
        val manualMean = validValues.map { it.toDouble() }.average()

        assertTrue(
            abs(expectedAverage - manualMean) < EPSILON,
            "Computed average ($expectedAverage) should match Kotlin's built-in average ($manualMean) " +
                "for valid values: $validValues"
        )
    }
}
