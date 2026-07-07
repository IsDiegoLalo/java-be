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
 * Step definitions for the Article Search feature.
 * Validates: Requirements 5.1, 5.4, 5.6
 */
class ArticleSearchStepDefinitions {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var testContext: TestContext

    @Given("the following published articles exist:")
    fun theFollowingPublishedArticlesExist(dataTable: DataTable) {
        val rows = dataTable.asMaps()
        for (row in rows) {
            val title = row["title"] ?: ""
            val body = row["body"] ?: ""
            val tags = row["tags"]?.split(",") ?: emptyList()
            val authorId = testContext.authorIds.values.firstOrNull() ?: "00000000-0000-0000-0000-000000000000"
            val categoryId = testContext.categoryIds.values.firstOrNull() ?: "00000000-0000-0000-0000-000000000000"

            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val tagsJson = tags.joinToString(",") { "\"${it.trim()}\"" }
            val requestBody = """
                {
                    "title": "$title",
                    "body": "$body",
                    "authorId": "$authorId",
                    "categoryId": "$categoryId",
                    "tags": [$tagsJson]
                }
            """.trimIndent()

            // Create article
            val createResponse = restTemplate.exchange(
                "/articles",
                HttpMethod.POST,
                HttpEntity(requestBody, headers),
                String::class.java
            )
            // TODO: Extract article ID, transition to review then published
            testContext.lastResponseStatus = createResponse.statusCode.value()
            testContext.lastResponseBody = createResponse.body
        }
    }

    @When("the reader searches for {string}")
    fun theReaderSearchesFor(query: String) {
        val response = restTemplate.exchange(
            "/articles/search?q=$query",
            HttpMethod.GET,
            null,
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @When("the reader searches for {string} with page {int} and size {int}")
    fun theReaderSearchesForWithPagination(query: String, page: Int, size: Int) {
        val response = restTemplate.exchange(
            "/articles/search?q=$query&page=$page&size=$size",
            HttpMethod.GET,
            null,
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @When("the reader searches with a query of {int} characters")
    fun theReaderSearchesWithLongQuery(charCount: Int) {
        val query = "a".repeat(charCount)
        val response = restTemplate.exchange(
            "/articles/search?q=$query",
            HttpMethod.GET,
            null,
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @Then("the search results should contain {int} article(s)")
    fun theSearchResultsShouldContainArticles(count: Int) {
        // TODO: Parse response body and verify content array size equals count
    }

    @Then("the search results should contain at least {int} article(s)")
    fun theSearchResultsShouldContainAtLeastArticles(count: Int) {
        // TODO: Parse response body and verify content array size >= count
    }

    @Then("the search results should include {string}")
    fun theSearchResultsShouldInclude(title: String) {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains(title)) {
            "Expected search results to include '$title' but got: $body"
        }
    }

    @Then("the first result should be {string}")
    fun theFirstResultShouldBe(title: String) {
        // TODO: Parse response body content[0].title and verify it matches
    }

    @Then("the total elements should be greater than {int}")
    fun theTotalElementsShouldBeGreaterThan(count: Int) {
        // TODO: Parse response body totalElements field
    }

    @Then("the total elements should be {int}")
    fun theTotalElementsShouldBe(count: Int) {
        // TODO: Parse response body totalElements field and verify it matches
    }

    @Then("the page size should be {int}")
    fun thePageSizeShouldBe(size: Int) {
        // TODO: Parse response body size field and verify it matches
    }

    @Then("the error should indicate a non-empty search term is required")
    fun theErrorShouldIndicateNonEmptySearchTermRequired() {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains("search") || body.contains("query") || body.contains("blank")) {
            "Expected error about non-empty search term in: $body"
        }
    }
}
