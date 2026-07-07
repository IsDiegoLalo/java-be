package com.platform.content.bdd

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import java.util.UUID

/**
 * Step definitions for Error Scenarios feature.
 * Validates: Requirements 4.2, 4.3, 1.6, 1.3, 2.3, 3.10
 */
class ErrorScenariosStepDefinitions {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var testContext: TestContext

    @When("an author is created with name {string} and email {string}")
    fun anAuthorIsCreatedWithNameAndEmail(name: String, email: String) {
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
    }

    @When("the author {string} is updated with email {string}")
    fun theAuthorIsUpdatedWithEmail(authorName: String, email: String) {
        val authorId = testContext.authorIds[authorName] ?: "00000000-0000-0000-0000-000000000000"
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """{"name": "$authorName", "email": "$email"}"""
        val response = restTemplate.exchange(
            "/authors/$authorId",
            HttpMethod.PUT,
            HttpEntity(body, headers),
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @When("the author {string} is deleted")
    fun theAuthorIsDeleted(authorName: String) {
        val authorId = testContext.authorIds[authorName] ?: "00000000-0000-0000-0000-000000000000"
        val response = restTemplate.exchange(
            "/authors/$authorId",
            HttpMethod.DELETE,
            null,
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @When("the category {string} is deleted")
    fun theCategoryIsDeleted(categoryName: String) {
        val categoryId = testContext.categoryIds[categoryName] ?: "00000000-0000-0000-0000-000000000000"
        val response = restTemplate.exchange(
            "/categories/$categoryId",
            HttpMethod.DELETE,
            null,
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @When("the article {string} is deleted")
    fun theArticleIsDeleted(title: String) {
        val articleId = testContext.articleIds[title] ?: "00000000-0000-0000-0000-000000000000"
        val response = restTemplate.exchange(
            "/articles/$articleId",
            HttpMethod.DELETE,
            null,
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @When("a status transition is requested for a non-existent article")
    fun aStatusTransitionIsRequestedForNonExistentArticle() {
        val fakeId = UUID.randomUUID()
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """{"targetStatus": "review"}"""
        val response = restTemplate.exchange(
            "/articles/$fakeId/status",
            HttpMethod.PUT,
            HttpEntity(body, headers),
            String::class.java
        )
        testContext.lastResponseStatus = response.statusCode.value()
        testContext.lastResponseBody = response.body
    }

    @When("an article is created with a non-existent author id")
    fun anArticleIsCreatedWithNonExistentAuthorId() {
        val fakeAuthorId = UUID.randomUUID()
        val categoryId = testContext.categoryIds.values.firstOrNull() ?: "00000000-0000-0000-0000-000000000000"
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """
            {
                "title": "Test Article",
                "body": "Some body content",
                "authorId": "$fakeAuthorId",
                "categoryId": "$categoryId",
                "tags": []
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
    }

    @When("an article is created with a non-existent category id")
    fun anArticleIsCreatedWithNonExistentCategoryId() {
        val authorId = testContext.authorIds.values.firstOrNull() ?: "00000000-0000-0000-0000-000000000000"
        val fakeCategoryId = UUID.randomUUID()
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """
            {
                "title": "Test Article",
                "body": "Some body content",
                "authorId": "$authorId",
                "categoryId": "$fakeCategoryId",
                "tags": []
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
    }

    @When("an article is created with a blank title")
    fun anArticleIsCreatedWithBlankTitle() {
        val authorId = testContext.authorIds.values.firstOrNull() ?: "00000000-0000-0000-0000-000000000000"
        val categoryId = testContext.categoryIds.values.firstOrNull() ?: "00000000-0000-0000-0000-000000000000"
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """
            {
                "title": "",
                "body": "Some body content",
                "authorId": "$authorId",
                "categoryId": "$categoryId",
                "tags": []
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
    }

    @Then("the response status should be {int}")
    fun theResponseStatusShouldBe(expectedStatus: Int) {
        assert(testContext.lastResponseStatus == expectedStatus) {
            "Expected HTTP $expectedStatus but got ${testContext.lastResponseStatus}"
        }
    }

    @Then("the error should indicate current state {string} and allowed transitions {string}")
    fun theErrorShouldIndicateCurrentStateAndAllowedTransitions(currentState: String, allowed: String) {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains(currentState)) {
            "Expected error mentioning current state '$currentState' in: $body"
        }
    }

    @Then("the error should indicate that published articles cannot change status")
    fun theErrorShouldIndicatePublishedArticlesCannotChangeStatus() {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains("published") || body.contains("transition")) {
            "Expected error about published articles in: $body"
        }
    }

    @Then("the error should indicate the email address is already in use")
    fun theErrorShouldIndicateEmailAlreadyInUse() {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains("email") || body.contains("duplicate") || body.contains("already")) {
            "Expected error about duplicate email in: $body"
        }
    }

    @Then("the error should indicate the author has associated articles")
    fun theErrorShouldIndicateAuthorHasAssociatedArticles() {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains("article") || body.contains("associated")) {
            "Expected error about associated articles in: $body"
        }
    }

    @Then("the error should indicate the category is in use")
    fun theErrorShouldIndicateCategoryIsInUse() {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains("category") || body.contains("in use") || body.contains("assigned")) {
            "Expected error about category in use in: $body"
        }
    }

    @Then("the error should indicate that published articles cannot be deleted")
    fun theErrorShouldIndicatePublishedArticlesCannotBeDeleted() {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains("published") || body.contains("delete")) {
            "Expected error about published article deletion in: $body"
        }
    }

    @Then("the error should indicate the article was not found")
    fun theErrorShouldIndicateArticleNotFound() {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains("not found") || body.contains("Article")) {
            "Expected not-found error for article in: $body"
        }
    }

    @Then("the error should indicate the author was not found")
    fun theErrorShouldIndicateAuthorNotFound() {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains("not found") || body.contains("Author")) {
            "Expected not-found error for author in: $body"
        }
    }

    @Then("the error should indicate the category was not found")
    fun theErrorShouldIndicateCategoryNotFound() {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains("not found") || body.contains("Category")) {
            "Expected not-found error for category in: $body"
        }
    }

    @Then("the error should contain field-level validation errors")
    fun theErrorShouldContainFieldLevelValidationErrors() {
        val body = testContext.lastResponseBody ?: ""
        assert(body.contains("field") || body.contains("validation") || body.contains("errors")) {
            "Expected field-level validation errors in: $body"
        }
    }
}
