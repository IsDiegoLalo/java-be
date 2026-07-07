Feature: Editorial Workflow
  As an editor, I want articles to progress through a defined editorial workflow,
  so that content quality is ensured before publication.

  Background:
    Given an author "John Doe" with email "john@example.com" exists
    And a category "Technology" exists

  Scenario: Author creates an article in draft status
    When the author creates an article titled "Kotlin Guide" in category "Technology"
    Then the article should be created successfully
    And the article status should be "draft"
    And the article published_at should be null

  Scenario: Author submits article for review
    Given an article "Kotlin Guide" exists in "draft" status
    When the article is submitted for review
    Then the article status should be "review"
    And the article updated_at should be updated

  Scenario: Editor publishes article from review
    Given an article "Kotlin Guide" exists in "review" status
    When the editor publishes the article
    Then the article status should be "published"
    And the article published_at should be set
    And the article updated_at should be updated

  Scenario: Full editorial workflow with event emission
    Given an article "Kotlin Guide" exists in "draft" status
    When the article is submitted for review
    Then the article status should be "review"
    When the editor publishes the article
    Then the article status should be "published"
    And an article published event should be emitted
    And the event payload should contain the article id
    And the event payload should contain the title "Kotlin Guide"
    And the event payload should contain the author id
    And the event payload should contain the category "Technology"
    And the event payload should contain the published_at timestamp

  Scenario: Editor sends article back to draft from review
    Given an article "Kotlin Guide" exists in "review" status
    When the article status is transitioned to "draft"
    Then the article status should be "draft"
    And the article updated_at should be updated
