Feature: Error Scenarios
  As a developer, I want predictable error responses for invalid operations,
  so that API consumers can handle failures gracefully.

  Background:
    Given an author "Error Test Author" with email "error-test@example.com" exists
    And a category "Error Category" exists

  # Invalid Workflow Transitions

  Scenario: Cannot transition draft article directly to published
    Given an article "Draft Only" exists in "draft" status
    When the article status is transitioned to "published"
    Then the response status should be 422
    And the error should indicate current state "draft" and allowed transitions "review"

  Scenario: Cannot transition published article to any status
    Given an article "Published Article" exists in "published" status
    When the article status is transitioned to "draft"
    Then the response status should be 422
    And the error should indicate that published articles cannot change status

  Scenario: Cannot transition published article to review
    Given an article "Published Article" exists in "published" status
    When the article status is transitioned to "review"
    Then the response status should be 422
    And the error should indicate that published articles cannot change status

  # Duplicate Email

  Scenario: Cannot create author with duplicate email
    Given an author "Error Test Author" with email "error-test@example.com" exists
    When an author is created with name "Another Author" and email "error-test@example.com"
    Then the response status should be 409
    And the error should indicate the email address is already in use

  Scenario: Cannot update author to use another author's email
    Given an author "Second Author" with email "second@example.com" exists
    When the author "Second Author" is updated with email "error-test@example.com"
    Then the response status should be 409
    And the error should indicate the email address is already in use

  # Deletion Guards

  Scenario: Cannot delete author with articles
    Given an article "Author's Article" exists in "draft" status
    When the author "Error Test Author" is deleted
    Then the response status should be 409
    And the error should indicate the author has associated articles

  Scenario: Cannot delete category with articles
    Given an article "Category Article" exists in "draft" status
    When the category "Error Category" is deleted
    Then the response status should be 409
    And the error should indicate the category is in use

  Scenario: Cannot delete published article
    Given an article "Published For Delete" exists in "published" status
    When the article "Published For Delete" is deleted
    Then the response status should be 409
    And the error should indicate that published articles cannot be deleted

  # Not Found Errors

  Scenario: Transitioning non-existent article returns 404
    When a status transition is requested for a non-existent article
    Then the response status should be 404
    And the error should indicate the article was not found

  Scenario: Creating article with non-existent author returns 404
    When an article is created with a non-existent author id
    Then the response status should be 404
    And the error should indicate the author was not found

  Scenario: Creating article with non-existent category returns 404
    When an article is created with a non-existent category id
    Then the response status should be 404
    And the error should indicate the category was not found

  # Validation Errors

  Scenario: Creating author with invalid email returns 422
    When an author is created with name "Valid Name" and email "not-an-email"
    Then the response status should be 422
    And the error should contain field-level validation errors

  Scenario: Creating article with blank title returns 422
    When an article is created with a blank title
    Then the response status should be 422
    And the error should contain field-level validation errors

  Scenario: Search query exceeding 200 characters is rejected
    When the reader searches with a query of 201 characters
    Then the response status should be 422
