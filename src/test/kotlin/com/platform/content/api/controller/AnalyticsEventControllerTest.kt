package com.platform.content.api.controller

import com.platform.content.api.dto.InteractionRequest
import com.platform.content.api.dto.PageViewRequest
import com.platform.content.api.dto.ReadTimeRequest
import com.platform.content.application.analytics.EngagementService
import com.platform.content.domain.model.InteractionType
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class AnalyticsEventControllerTest {

    private lateinit var engagementService: EngagementService
    private lateinit var controller: AnalyticsEventController

    @BeforeEach
    fun setUp() {
        engagementService = mockk()
        controller = AnalyticsEventController(engagementService)
    }

    @Test
    fun `recordPageView should return 202 Accepted`() {
        val articleId = UUID.randomUUID()
        val request = PageViewRequest(articleId = articleId)
        every { engagementService.recordPageView(articleId) } just runs

        val response = controller.recordPageView(request)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        verify { engagementService.recordPageView(articleId) }
    }

    @Test
    fun `recordReadTime should return 202 Accepted with valid seconds`() {
        val articleId = UUID.randomUUID()
        val request = ReadTimeRequest(articleId = articleId, seconds = 120)
        every { engagementService.recordReadTime(articleId, 120) } just runs

        val response = controller.recordReadTime(request)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        verify { engagementService.recordReadTime(articleId, 120) }
    }

    @Test
    fun `recordReadTime should accept minimum valid value of 1 second`() {
        val articleId = UUID.randomUUID()
        val request = ReadTimeRequest(articleId = articleId, seconds = 1)
        every { engagementService.recordReadTime(articleId, 1) } just runs

        val response = controller.recordReadTime(request)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        verify { engagementService.recordReadTime(articleId, 1) }
    }

    @Test
    fun `recordReadTime should accept maximum valid value of 3600 seconds`() {
        val articleId = UUID.randomUUID()
        val request = ReadTimeRequest(articleId = articleId, seconds = 3600)
        every { engagementService.recordReadTime(articleId, 3600) } just runs

        val response = controller.recordReadTime(request)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        verify { engagementService.recordReadTime(articleId, 3600) }
    }

    @Test
    fun `recordInteraction should return 202 Accepted for LIKE`() {
        val articleId = UUID.randomUUID()
        val request = InteractionRequest(articleId = articleId, type = "LIKE")
        every { engagementService.recordInteraction(articleId, InteractionType.LIKE) } just runs

        val response = controller.recordInteraction(request)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        verify { engagementService.recordInteraction(articleId, InteractionType.LIKE) }
    }

    @Test
    fun `recordInteraction should return 202 Accepted for SHARE`() {
        val articleId = UUID.randomUUID()
        val request = InteractionRequest(articleId = articleId, type = "SHARE")
        every { engagementService.recordInteraction(articleId, InteractionType.SHARE) } just runs

        val response = controller.recordInteraction(request)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        verify { engagementService.recordInteraction(articleId, InteractionType.SHARE) }
    }

    @Test
    fun `recordInteraction should return 202 Accepted for COMMENT`() {
        val articleId = UUID.randomUUID()
        val request = InteractionRequest(articleId = articleId, type = "COMMENT")
        every { engagementService.recordInteraction(articleId, InteractionType.COMMENT) } just runs

        val response = controller.recordInteraction(request)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        verify { engagementService.recordInteraction(articleId, InteractionType.COMMENT) }
    }

    @Test
    fun `recordInteraction should throw IllegalArgumentException for invalid type`() {
        val articleId = UUID.randomUUID()
        val request = InteractionRequest(articleId = articleId, type = "INVALID")

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            controller.recordInteraction(request)
        }
    }
}
