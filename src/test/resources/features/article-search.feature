Feature: Article Search
  As a reader, I want to search articles by keywords,
  so that I can find relevant content quickly.

  Background:
    Given an author "Jane Smith" with email "jane@example.com" exists
    And a category "Science" exists
    And the following published articles exist:
      | title                     | body                                         | tags        |
      | Quantum Computing Basics  | An introduction to quantum computing concepts | quantum,cs  |
      | Machine Learning Guide    | Deep dive into machine learning algorithms    | ml,ai       |
      | Classical Physics Review  | Overview of Newtonian mechanics               | physics     |

  Scenario: Reader searches for articles by keyword
    When the reader searches for "quantum"
    Then the search results should contain 1 article
    And the search results should include "Quantum Computing Basics"

  Scenario: Search returns only published articles
    Given an article "Draft Article" exists in "draft" status
    When the reader searches for "Draft Article"
    Then the search results should contain 0 articles

  Scenario: Search returns results ranked by relevance
    When the reader searches for "computing"
    Then the search results should contain at least 1 article
    And the first result should be "Quantum Computing Basics"

  Scenario: Search supports pagination
    When the reader searches for "guide" with page 0 and size 1
    Then the search results should contain 1 article
    And the total elements should be greater than 0
    And the page size should be 1

  Scenario: Search with no matches returns empty results
    When the reader searches for "nonexistentterm12345"
    Then the search results should contain 0 articles
    And the total elements should be 0

  Scenario: Empty search query is rejected
    When the reader searches for ""
    Then the response status should be 422
    And the error should indicate a non-empty search term is required
