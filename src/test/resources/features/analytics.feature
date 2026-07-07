Feature: Engagement Analytics
  As a product owner, I want to track and retrieve article engagement metrics,
  so that content performance can be measured.

  Background:
    Given an author "Alice Writer" with email "alice@example.com" exists
    And a category "Technology" exists
    And a published article "Popular Article" by "Alice Writer" in "Technology" exists

  Scenario: Record page view for an article
    When a page view event is recorded for the article "Popular Article"
    Then the engagement metrics for "Popular Article" should show 1 page view

  Scenario: Record multiple page views
    When 5 page view events are recorded for the article "Popular Article"
    Then the engagement metrics for "Popular Article" should show 5 page views

  Scenario: Record read time for an article
    When a read time of 120 seconds is recorded for "Popular Article"
    Then the engagement metrics for "Popular Article" should show average read time of 120.0 seconds

  Scenario: Record multiple read times computes running average
    When the following read times are recorded for "Popular Article":
      | seconds |
      | 60      |
      | 120     |
      | 180     |
    Then the engagement metrics for "Popular Article" should show average read time of 120.0 seconds

  Scenario: Record interaction events
    When a "LIKE" interaction is recorded for "Popular Article"
    And a "SHARE" interaction is recorded for "Popular Article"
    And a "COMMENT" interaction is recorded for "Popular Article"
    Then the engagement metrics for "Popular Article" should show:
      | likes | shares | comments |
      | 1     | 1      | 1        |

  Scenario: Retrieve author aggregated analytics
    Given a published article "Second Article" by "Alice Writer" in "Technology" exists
    And 10 page view events are recorded for "Popular Article"
    And 5 page view events are recorded for "Second Article"
    When the aggregated analytics for author "Alice Writer" are retrieved
    Then the total page views should be 15

  Scenario: Analytics for article with no engagement returns zeros
    Given a published article "New Article" by "Alice Writer" in "Technology" exists
    When the engagement metrics for "New Article" are retrieved
    Then the engagement metrics should show 0 page views
    And the engagement metrics should show average read time of 0.0 seconds
    And the engagement metrics should show 0 likes
    And the engagement metrics should show 0 shares
    And the engagement metrics should show 0 comments
