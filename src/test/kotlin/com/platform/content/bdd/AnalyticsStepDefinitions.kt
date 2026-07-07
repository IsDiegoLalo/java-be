package com.platform.content.bdd

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType

/**
 * Step definitions for the Engagement Analytics feature.
 * Validates: Requirements 6.1, 7.1
 */
class AnalyticsStepDefinitions {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var testContext: TestContext

    @Given("a published article {string} by {string} in {string} exists")
    fun aPublishedArticleByAuthorInCategoryExists(title: String, authorName: String, categoryName: String) {
        val authorId = testContext.authorIds[authorName] ?: "00000000-0000-0000-0000-000000000000"
        val categoryId = testContext.categoryIds[categoryName] ?: "00000000-0000-0000-0000-000000000000"
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """
            {
                "title": "$title",
                "body": "Body content for $title",
                "authorId": "$authorId",
                "categoryId": "$categoryId",
                "tags": []
            }
        """.trimIndent()

        // Create article
        restTemplate.exchange(
            "/articles",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java
        )
        // TODO: Extract article ID, transition draft -> review -> published
        // TODO: Store in testContext.articleIds[title]
    }

    @When("a page view event is recorded for the article {string}")
    fun aPageViewEventIsRecordedForArticle(title: String) {
        val articleId = testContext.articleIds[title] ?: "00000000-0000-0000-0000-000000000000"
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """{"articleId": "$articleId"}"""
        val response = restTemplate.exchange(
            "/analytics/events/page-view",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @When("{int} page view events are recorded for the article {string}")
    fun multiplePageViewEventsAreRecordedForArticle(count: Int, title: String) {
        repeat(count) {
            aPageViewEventIsRecordedForArticle(title)
        }
    }

    @Given("{int} page view events are recorded for {string}")
    fun givenMultiplePageViewEventsAreRecorded(count: Int, title: String) {
        repeat(count) {
            aPageViewEventIsRecordedForArticle(title)
        }
    }

    @When("a read time of {int} seconds is recorded for {string}")
    fun aReadTimeIsRecordedForArticle(seconds: Int, title: String) {
        val articleId = testContext.articleIds[title] ?: "00000000-0000-0000-0000-000000000000"
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """{"articleId": "$articleId", "seconds": $seconds}"""
        val response = restTemplate.exchange(
            "/analytics/events/read-time",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @When("the following read times are recorded for {string}:")
    fun theFollowingReadTimesAreRecorded(title: String, dataTable: DataTable) {
        val rows = dataTable.asMaps()
        for (row in rows) {
            val seconds = row["seconds"]?.toInt() ?: 0
            aReadTimeIsRecordedForArticle(seconds, title)
        }
    }

    @When("a {string} interaction is recorded for {string}")
    fun anInteractionIsRecordedForArticle(type: String, title: String) {
        val articleId = testContext.articleIds[title] ?: "00000000-0000-0000-0000-000000000000"
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """{"articleId": "$articleId", "type": "$type"}"""
        val response = restTemplate.exchange(
            "/analytics/events/interaction",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @When("the engagement metrics for {string} are retrieved")
    fun theEngagementMetricsForArticleAreRetrieved(title: String) {
        val articleId = testContext.articleIds[title] ?: "00000000-0000-0000-0000-000000000000"
        val response = restTemplate.exchange(
            "/analytics/articles/$articleId",
            HttpMethod.GET,
            null,
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @When("the aggregated analytics for author {string} are retrieved")
    fun theAggregatedAnalyticsForAuthorAreRetrieved(authorName: String) {
        val authorId = testContext.authorIds[authorName] ?: "00000000-0000-0000-0000-000000000000"
        val response = restTemplate.exchange(
            "/analytics/authors/$authorId",
            HttpMethod.GET,
            null,
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @Then("the engagement metrics for {string} should show {int} page view(s)")
    fun theEngagementMetricsShouldShowPageViews(title: String, expectedCount: Int) {
        theEngagementMetricsForArticleAreRetrieved(title)
        // TODO: Parse response body and verify pageViews field equals expectedCount
    }

    @Then("the engagement metrics for {string} should show average read time of {double} seconds")
    fun theEngagementMetricsShouldShowAverageReadTime(title: String, expectedAvg: Double) {
        theEngagementMetricsForArticleAreRetrieved(title)
        // TODO: Parse response body and verify averageReadTimeSeconds field
    }

    @Then("the engagement metrics for {string} should show:")
    fun theEngagementMetricsShouldShow(title: String, dataTable: DataTable) {
        theEngagementMetricsForArticleAreRetrieved(title)
        // TODO: Parse response body and verify likes, shares, comments match table
    }

    @Then("the total page views should be {int}")
    fun theTotalPageViewsShouldBe(expectedTotal: Int) {
        // TODO: Parse response body totalPageViews field and verify
    }

    @Then("the engagement metrics should show {int} page views")
    fun theEngagementMetricsShouldShowPageViewsCount(count: Int) {
        // TODO: Parse response body pageViews field
    }

    @Then("the engagement metrics should show average read time of {double} seconds")
    fun theEngagementMetricsShouldShowAvgReadTime(avg: Double) {
        // TODO: Parse response body averageReadTimeSeconds field
    }

    @Then("the engagement metrics should show {int} likes")
    fun theEngagementMetricsShouldShowLikes(count: Int) {
        // TODO: Parse response body likes field
    }

    @Then("the engagement metrics should show {int} shares")
    fun theEngagementMetricsShouldShowShares(count: Int) {
        // TODO: Parse response body shares field
    }

    @Then("the engagement metrics should show {int} comments")
    fun theEngagementMetricsShouldShowComments(count: Int) {
        // TODO: Parse response body comments field
    }
}
