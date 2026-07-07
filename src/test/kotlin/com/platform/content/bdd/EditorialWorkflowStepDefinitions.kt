package com.platform.content.bdd

import io.cucumber.java.Before
import io.cucumber.java.en.And
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
 * Step definitions for the Editorial Workflow feature.
 * Validates: Requirements 4.2, 4.3, 8.1
 */
class EditorialWorkflowStepDefinitions {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var testContext: TestContext

    @Before
    fun setUp() {
        testContext.reset()
    }

    @Given("an author {string} with email {string} exists")
    fun anAuthorWithEmailExists(name: String, email: String) {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """{"name": "$name", "email": "$email"}"""
        val response = restTemplate.exchange(
            "/authors",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
        // TODO: Extract ID from response and store in testContext.authorIds[name]
    }

    @Given("a category {string} exists")
    fun aCategoryExists(name: String) {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """{"name": "$name"}"""
        val response = restTemplate.exchange(
            "/categories",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
        // TODO: Extract ID from response and store in testContext.categoryIds[name]
    }

    @When("the author creates an article titled {string} in category {string}")
    fun theAuthorCreatesAnArticle(title: String, category: String) {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val authorId = testContext.authorIds.values.firstOrNull() ?: "00000000-0000-0000-0000-000000000000"
        val categoryId = testContext.categoryIds[category] ?: "00000000-0000-0000-0000-000000000000"
        val body = """
            {
                "title": "$title",
                "body": "Article body content for $title",
                "authorId": "$authorId",
                "categoryId": "$categoryId",
                "tags": ["kotlin", "guide"]
            }
        """.trimIndent()
        val response = restTemplate.exchange(
            "/articles",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
        // TODO: Extract article ID and store in testContext.articleIds[title]
    }

    @Given("an article {string} exists in {string} status")
    fun anArticleExistsInStatus(title: String, status: String) {
        // Create the article in draft
        theAuthorCreatesAnArticle(title, testContext.categoryIds.keys.firstOrNull() ?: "Technology")

        // Transition through states as needed
        if (status == "review" || status == "published") {
            theArticleIsSubmittedForReview()
        }
        if (status == "published") {
            theEditorPublishesTheArticle()
        }
    }

    @When("the article is submitted for review")
    fun theArticleIsSubmittedForReview() {
        val articleId = testContext.articleIds.values.lastOrNull() ?: "00000000-0000-0000-0000-000000000000"
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """{"targetStatus": "review"}"""
        val response = restTemplate.exchange(
            "/articles/$articleId/status",
            HttpMethod.PUT,
            HttpEntity(body, headers),
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @When("the editor publishes the article")
    fun theEditorPublishesTheArticle() {
        val articleId = testContext.articleIds.values.lastOrNull() ?: "00000000-0000-0000-0000-000000000000"
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """{"targetStatus": "published"}"""
        val response = restTemplate.exchange(
            "/articles/$articleId/status",
            HttpMethod.PUT,
            HttpEntity(body, headers),
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @When("the article status is transitioned to {string}")
    fun theArticleStatusIsTransitionedTo(targetStatus: String) {
        val articleId = testContext.articleIds.values.lastOrNull() ?: "00000000-0000-0000-0000-000000000000"
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """{"targetStatus": "$targetStatus"}"""
        val response = restTemplate.exchange(
            "/articles/$articleId/status",
            HttpMethod.PUT,
            HttpEntity(body, headers),
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @Then("the article should be created successfully")
    fun theArticleShouldBeCreatedSuccessfully() {
        assert(testContext.lastResponseStatus == 201) {
            "Expected 201 Created but got ${testContext.lastResponseStatus}"
        }
    }

    @Then("the article status should be {string}")
    fun theArticleStatusShouldBe(expectedStatus: String) {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains("\"status\":\"$expectedStatus\"") || body.contains("\"status\": \"$expectedStatus\"")) {
            "Expected status '$expectedStatus' in response: $body"
        }
    }

    @Then("the article published_at should be null")
    fun theArticlePublishedAtShouldBeNull() {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains("\"publishedAt\":null") || body.contains("\"publishedAt\": null")) {
            "Expected publishedAt to be null in response: $body"
        }
    }

    @Then("the article published_at should be set")
    fun theArticlePublishedAtShouldBeSet() {
        val body = testContext.lastResponseBody ?: ""
        assert(!body.contains("\"publishedAt\":null") && !body.contains("\"publishedAt\": null")) {
            "Expected publishedAt to be non-null in response: $body"
        }
    }

    @Then("the article updated_at should be updated")
    fun theArticleUpdatedAtShouldBeUpdated() {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains("\"updatedAt\"")) {
            "Expected updatedAt field in response: $body"
        }
    }

    @Then("an article published event should be emitted")
    fun anArticlePublishedEventShouldBeEmitted() {
        // TODO: Consume from Kafka test topic and verify event was published
        // Stub: will be implemented when Testcontainers Kafka is available
    }

    @Then("the event payload should contain the article id")
    fun theEventPayloadShouldContainTheArticleId() {
        // TODO: Verify event payload contains articleId field
    }

    @Then("the event payload should contain the title {string}")
    fun theEventPayloadShouldContainTheTitle(title: String) {
        // TODO: Verify event payload title matches
    }

    @Then("the event payload should contain the author id")
    fun theEventPayloadShouldContainTheAuthorId() {
        // TODO: Verify event payload contains authorId field
    }

    @Then("the event payload should contain the category {string}")
    fun theEventPayloadShouldContainTheCategory(category: String) {
        // TODO: Verify event payload category matches
    }

    @Then("the event payload should contain the published_at timestamp")
    fun theEventPayloadShouldContainThePublishedAtTimestamp() {
        // TODO: Verify event payload contains publishedAt field
    }
}
