# Implementation Plan: Content Publishing Platform

## Overview

This plan implements a Spring Boot 3.x microservice (Java 21 / Kotlin) using hexagonal architecture. The implementation progresses from project scaffolding and domain models through infrastructure adapters, REST controllers, and finally integration wiring. Each step builds incrementally on the previous, ensuring no orphaned code.

## Tasks

- [x] 1. Set up project structure, build configuration, and core domain models
  - [x] 1.1 Initialize Gradle (Kotlin DSL) project with Spring Boot 3.x, Kotlin, and all required dependencies
    - Create `build.gradle.kts` with plugins: Spring Boot, Kotlin JVM, Kotlin Spring, JPA
    - Add dependencies: Spring Web, Spring Data JPA, Spring Data MongoDB, Spring Kafka, Flyway, PostgreSQL driver, Jackson, Bean Validation
    - Add test dependencies: JUnit 5, jqwik, MockK, Testcontainers (PostgreSQL, MongoDB, Kafka), Cucumber
    - Configure JDK 21 toolchain and Kotlin compiler options
    - Create `set tings.gradle.kts` with project name
    - _Requirements: 13.4_

  - [x] 1.2 Create package structure and application entry point
    - Create the package layout under `com.platform.content` following the hexagonal architecture: `api/`, `domain/`, `application/`, `infrastructure/`
    - Create `ContentPublishingApplication.kt` main class with `@SpringBootApplication`
    - Create `application.yml` with profiles for default, test, and docker configurations
    - _Requirements: 11.5_

  - [x] 1.3 Implement domain model entities and value objects
    - Create `Author`, `Category`, `Article` data classes in `domain/model/`
    - Create `ArticleStatus` sealed class (Draft, Review, Published) with `fromString` companion method
    - Create `EngagementRecord`, `InteractionCounts`, `AggregatedEngagement` data classes
    - Create `ArticlePublishedEvent` data class
    - Create `OutboxEvent` data class and `OutboxEventStatus` enum
    - Create `InteractionType` enum (LIKE, SHARE, COMMENT)
    - _Requirements: 3.2, 3.3, 4.1, 6.2, 8.2_

  - [x] 1.4 Define domain port interfaces
    - Create `ArticleRepository` interface in `domain/port/`
    - Create `AuthorRepository` interface in `domain/port/`
    - Create `CategoryRepository` interface in `domain/port/`
    - Create `ArticleSearchPort` interface in `domain/port/`
    - Create `EngagementWritePort` and `EngagementReadPort` interfaces in `domain/port/`
    - Create `ArticleEventPublisher` interface in `domain/port/`
    - Create `OutboxStore` interface in `domain/port/`
    - _Requirements: 1.1, 2.1, 3.1, 5.1, 6.1, 7.1, 8.1_

  - [x] 1.5 Implement domain exception hierarchy
    - Create sealed class `DomainException` in `domain/`
    - Create `EntityNotFoundException`, `ConflictException`, `InvalidTransitionException`, `ValidationException`, `EventPublishingException`
    - _Requirements: 10.2, 10.5, 10.6_

- [x] 2. Implement editorial workflow and domain validation logic
  - [x] 2.1 Implement the editorial workflow state machine
    - Create `WorkflowTransitionValidator` interface in `domain/workflow/`
    - Create `TransitionResult` sealed class (Valid, Invalid)
    - Implement `EditorialWorkflowValidator` class with transition rules: draft→review, review→published|draft, published→none
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 2.2 Write property test for editorial workflow state machine
    - **Property 10: Editorial workflow state machine**
    - Test that for any article status and any target status, the validator correctly allows/rejects transitions per the defined rules
    - Use jqwik to generate arbitrary status combinations
    - **Validates: Requirements 4.2, 4.3, 4.4, 4.5**

  - [x] 2.3 Implement Author validation logic
    - Create `AuthorValidator` in `domain/` or `application/author/`
    - Validate name: 1–100 chars, non-blank
    - Validate email: RFC 5322 format, max 255 chars, unique check
    - Validate bio: optional, max 500 chars
    - _Requirements: 1.2, 1.5_

  - [x] 2.4 Write property test for Author field validation
    - **Property 1: Author field validation**
    - Use jqwik to generate arbitrary string inputs for name, email, bio and verify acceptance/rejection
    - **Validates: Requirements 1.2, 1.5**

  - [x] 2.5 Implement Category validation and slug generation
    - Create `CategoryValidator` in `domain/` or `application/category/`
    - Validate name: 2–100 chars, non-blank, unique (case-insensitive)
    - Implement `SlugGenerator` utility: lowercase, hyphen-separated, URL-safe transliteration
    - _Requirements: 2.2, 2.5_

  - [x] 2.6 Write property test for Category name validation
    - **Property 4: Category name validation**
    - Use jqwik to generate arbitrary name strings and verify acceptance/rejection per constraints
    - **Validates: Requirements 2.2**

  - [x] 2.7 Write property test for slug generation invariants
    - **Property 5: Slug generation invariants**
    - Verify slug is lowercase, matches `[a-z0-9-]`, non-empty, and deterministic for any valid category name
    - **Validates: Requirements 2.5**

  - [x] 2.8 Implement Article validation logic
    - Create `ArticleValidator` in `domain/` or `application/article/`
    - Validate title: 1–255 chars, non-blank
    - Validate body: non-blank
    - Validate summary: optional, max 500 chars
    - Validate tags: max 10 entries
    - _Requirements: 3.8_

  - [x] 2.9 Write property test for Article field validation
    - **Property 8: Article field validation**
    - Use jqwik to generate arbitrary article inputs and verify correct acceptance/rejection
    - **Validates: Requirements 3.8**

- [x] 3. Checkpoint - Ensure domain model and validation tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement Flyway migrations and PostgreSQL persistence adapters
  - [x] 4.1 Create Flyway migration scripts
    - Create `V1__create_authors_table.sql` with id, name, email (unique), bio, created_at
    - Create `V2__create_categories_table.sql` with id, name (case-insensitive unique), description, slug (unique)
    - Create `V3__create_articles_table.sql` with all columns, FK constraints, status check constraint, indexes
    - Create `V4__create_search_vector_trigger.sql` with tsvector column, GIN index, and trigger for auto-update
    - Create `V5__create_outbox_events_table.sql` with outbox schema
    - Place scripts in `src/main/resources/db/migration/`
    - _Requirements: 9.1, 9.2, 9.3_

  - [x] 4.2 Implement JPA entity mappings for Author, Category, Article
    - Create `AuthorEntity`, `CategoryEntity`, `ArticleEntity` JPA entities in `infrastructure/persistence/`
    - Map to domain models with converter/mapper functions
    - Configure UUID generation strategy, column mappings, and relationship annotations
    - _Requirements: 1.4, 2.4, 3.3_

  - [x] 4.3 Implement Spring Data JPA repositories
    - Create `JpaAuthorRepository` implementing domain `AuthorRepository` port
    - Create `JpaCategoryRepository` implementing domain `CategoryRepository` port
    - Create `JpaArticleRepository` implementing domain `ArticleRepository` port with filtering and pagination support
    - Implement `existsByAuthorId`, `existsByCategoryId` queries
    - _Requirements: 1.1, 2.1, 3.1, 3.4, 3.5_

  - [x] 4.4 Implement PostgreSQL full-text search adapter
    - Create `PostgresArticleSearchAdapter` implementing `ArticleSearchPort`
    - Use native query with `ts_rank` and `to_tsquery` for relevance-ranked search
    - Filter to only published articles
    - Support pagination
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [x] 4.5 Implement Outbox store persistence adapter
    - Create `JpaOutboxStore` implementing `OutboxStore` port
    - Create `OutboxEventEntity` JPA entity
    - Implement `save`, `findPending`, `markDelivered`, `markFailed` operations
    - _Requirements: 8.4, 8.5_

- [x] 5. Implement MongoDB analytics adapters
  - [x] 5.1 Implement MongoDB engagement write adapter
    - Create `MongoEngagementWriteAdapter` implementing `EngagementWritePort`
    - Implement `recordPageView` with upsert (increment page views)
    - Implement `recordReadTime` with running average calculation
    - Implement `recordInteraction` with atomic increment of like/share/comment counts
    - Create MongoDB document class `EngagementDocument`
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [x] 5.2 Implement MongoDB engagement read adapter
    - Create `MongoEngagementReadAdapter` implementing `EngagementReadPort`
    - Implement `getByArticleId` returning engagement record (zeroed if none found)
    - Implement `getAggregatedByAuthorId` with aggregation pipeline (sum page views, weighted avg read time, sum interactions)
    - _Requirements: 7.1, 7.2, 7.3_

  - [x] 5.3 Write property test for read time average computation
    - **Property 16: Read time average computation**
    - Verify that for any sequence of valid read time values (1–3600), the stored average equals the arithmetic mean; invalid values are discarded
    - **Validates: Requirements 6.3, 6.7**

  - [x] 5.4 Write property test for interaction count increment
    - **Property 17: Interaction count increment**
    - Verify that for any sequence of interaction events, final counts match the event totals per type
    - **Validates: Requirements 6.4**

  - [x] 5.5 Write property test for author analytics aggregation
    - **Property 18: Author analytics aggregation**
    - Verify aggregated metrics satisfy sum/weighted-average invariants
    - **Validates: Requirements 7.2**

- [ ] 6. Implement application services (use cases)
  - [x] 6.1 Implement AuthorService
    - Create `AuthorService` in `application/author/`
    - Implement create (validate, check email uniqueness, save)
    - Implement read, update (re-validate, check email uniqueness), delete (guard: has articles?)
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.6_

  - [-] 6.2 Write property test for Author deletion guard
    - **Property 2: Author deletion guard**
    - Verify that deletion is always rejected when author has associated articles
    - **Validates: Requirements 1.3**

  - [-] 6.3 Write property test for duplicate email uniqueness
    - **Property 3: Duplicate email uniqueness**
    - Verify that creating/updating with a duplicate email is always rejected
    - **Validates: Requirements 1.6**

  - [x] 6.4 Implement CategoryService
    - Create `CategoryService` in `application/category/`
    - Implement create (validate, generate slug, check uniqueness), read, update, delete (guard: has articles?)
    - _Requirements: 2.1, 2.2, 2.3, 2.5_

  - [-] 6.5 Write property test for Category deletion guard
    - **Property 6: Category deletion guard**
    - Verify that deletion is always rejected when category has assigned articles
    - **Validates: Requirements 2.3**

  - [x] 6.6 Implement ArticleService
    - Create `ArticleService` in `application/article/`
    - Implement create (validate, check FK references, set draft status), read, update, list (filter + paginate), delete (guard: not published)
    - _Requirements: 3.1, 3.2, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10_

  - [-] 6.7 Write property test for new article default status
    - **Property 7: New article default status**
    - Verify that any valid article creation always results in draft status and null published_at
    - **Validates: Requirements 3.2**

  - [-] 6.8 Write property test for published article deletion guard
    - **Property 9: Published article deletion guard**
    - Verify that deletion of a published article is always rejected
    - **Validates: Requirements 3.10**

  - [x] 6.9 Implement ArticleWorkflowService
    - Create `ArticleWorkflowService` in `application/article/`
    - Implement `transitionStatus`: validate transition, update timestamps, save, emit event on publish
    - Set `published_at` on transition to published, always update `updated_at`
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8_

  - [-] 6.10 Write property test for transition timestamp updates
    - **Property 11: Transition timestamp updates**
    - Verify updated_at is set on any transition and published_at is set when transitioning to published
    - **Validates: Requirements 4.6, 4.7**

  - [x] 6.11 Implement SearchService
    - Create `SearchService` in `application/search/`
    - Validate query: non-blank, max 200 chars
    - Delegate to `ArticleSearchPort`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

  - [-] 6.12 Write property test for blank search query rejection
    - **Property 15: Blank search query rejection**
    - Verify that empty or whitespace-only queries are always rejected
    - **Validates: Requirements 5.6**

  - [x] 6.13 Implement EngagementService
    - Create `EngagementService` in `application/analytics/`
    - Implement async event recording: page views, read time (validate 1–3600), interactions
    - Implement retrieval: single article, author aggregation
    - Discard events for non-existent articles (log warning)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 7. Checkpoint - Ensure application services and property tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement Kafka event publisher with outbox pattern
  - [x] 8.1 Implement KafkaArticleEventPublisher
    - Create `KafkaArticleEventPublisher` implementing `ArticleEventPublisher` port
    - Serialize `ArticlePublishedEvent` to JSON payload
    - Write to outbox store first, then attempt Kafka send
    - Implement exponential backoff retry: 1s, 2s, 4s (3 attempts max)
    - On final failure: log at ERROR, mark event as FAILED in outbox
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [x] 8.2 Implement OutboxProcessor scheduled task
    - Create `OutboxProcessor` with `@Scheduled` annotation (every 30 seconds)
    - Query pending events from outbox store
    - Retry delivery to Kafka
    - Mark as delivered or failed based on result
    - _Requirements: 8.4, 8.5_

  - [x] 8.3 Write property test for event payload completeness
    - **Property 19: Event payload completeness**
    - Verify that for any published article, the event payload contains all required fields (article id, title, author id, category, tags, published_at) with none null
    - **Validates: Requirements 8.2**

- [x] 9. Implement REST API layer
  - [x] 9.1 Implement GlobalExceptionHandler with RFC 7807 Problem Details
    - Create `GlobalExceptionHandler` in `api/error/`
    - Map `EntityNotFoundException` → 404, `ConflictException` → 409, `InvalidTransitionException` → 422, `MethodArgumentNotValidException` → 422, `ValidationException` → 422
    - Include field-level errors for validation failures
    - _Requirements: 10.1, 10.2, 10.3, 10.5, 10.6_

  - [x] 9.2 Implement request/response DTOs and mappers
    - Create `CreateAuthorRequest`, `UpdateAuthorRequest`, `AuthorResponse` DTOs with Bean Validation annotations
    - Create `CreateCategoryRequest`, `UpdateCategoryRequest`, `CategoryResponse` DTOs
    - Create `CreateArticleRequest`, `UpdateArticleRequest`, `ArticleResponse` DTOs
    - Create `TransitionStatusRequest` DTO
    - Create `PageResponse<T>` wrapper with pagination metadata
    - Create `EngagementResponse`, `AggregatedEngagementResponse` DTOs
    - Create `PageViewRequest`, `ReadTimeRequest`, `InteractionRequest` DTOs
    - Implement mapper functions between DTOs and domain models
    - _Requirements: 10.1, 10.4, 12.1, 12.3, 12.4, 12.5_

  - [x] 9.3 Write property test for Article serialization round-trip
    - **Property 20: Article serialization round-trip**
    - Verify that serializing an Article to JSON and deserializing back produces field-by-field equivalent object with correct null handling, empty array handling, and ISO 8601 date format
    - **Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5**

  - [x] 9.4 Implement AuthorController
    - Create REST endpoints: POST /authors, GET /authors/{id}, PUT /authors/{id}, DELETE /authors/{id}
    - Apply `@Valid` for request body validation
    - Return appropriate HTTP status codes (201, 200, 204)
    - _Requirements: 1.1, 10.1_

  - [x] 9.5 Implement CategoryController
    - Create REST endpoints: POST /categories, GET /categories/{id}, PUT /categories/{id}, DELETE /categories/{id}
    - Apply `@Valid` for request body validation
    - Return appropriate HTTP status codes
    - _Requirements: 2.1, 10.1_

  - [x] 9.6 Implement ArticleController and ArticleWorkflowController
    - Create REST endpoints: POST /articles, GET /articles/{id}, PUT /articles/{id}, DELETE /articles/{id}, GET /articles (with filters and pagination)
    - Create PUT /articles/{id}/status for workflow transitions
    - Apply `@Valid` for request body validation
    - _Requirements: 3.1, 3.4, 3.5, 4.2, 10.1_

  - [x] 9.7 Implement SearchController
    - Create GET /articles/search?q={query}&page={page}&size={size} endpoint
    - Validate query parameter (non-blank, max 200 chars)
    - Return paginated search results
    - _Requirements: 5.1, 5.3, 5.6, 5.7, 10.1_

  - [x] 9.8 Implement AnalyticsController and AnalyticsEventController
    - Create GET /analytics/articles/{id} for single article metrics
    - Create GET /analytics/authors/{id} for author aggregated metrics
    - Create POST /analytics/events/page-view, POST /analytics/events/read-time, POST /analytics/events/interaction
    - _Requirements: 6.1, 7.1, 7.2, 10.1_

  - [x] 9.9 Write property test for pagination invariants
    - **Property 12: Pagination invariants**
    - Verify page size acceptance (1–100), default 20, and metadata consistency (totalPages = ceil(totalElements / size))
    - **Validates: Requirements 3.4, 5.3, 10.4**

  - [x] 9.10 Write property test for article list filtering correctness
    - **Property 13: Article list filtering correctness**
    - Verify that all returned articles satisfy every specified filter criterion
    - **Validates: Requirements 3.5**

  - [x] 9.11 Write property test for search returns only published articles
    - **Property 14: Search returns only published articles**
    - Verify that all articles in search results have status "published"
    - **Validates: Requirements 5.4**

- [x] 10. Checkpoint - Ensure REST API layer and all property tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Implement containerization and CI/CD
  - [x] 11.1 Create Dockerfile with multi-stage build
    - Stage 1: Build with Gradle and JDK 21
    - Stage 2: Runtime with JRE-only base image, non-root user
    - Configure health check endpoint
    - _Requirements: 11.1_

  - [x] 11.2 Create Docker Compose configuration
    - Define services: PostgreSQL 16, MongoDB 7, Kafka (with Zookeeper), application
    - Configure health checks with connection checks for each service
    - Define named volumes for PostgreSQL and MongoDB data persistence
    - Expose environment variables with working defaults for local dev
    - Set `depends_on` with health check conditions and 60-second timeout
    - _Requirements: 11.2, 11.3, 11.4, 11.5, 11.6_

  - [x] 11.3 Create GitHub Actions CI workflow
    - Create `.github/workflows/ci.yml`
    - Trigger on push and PR to main branch
    - Set up JDK 21, cache Gradle dependencies
    - Run unit tests, integration tests (Testcontainers), and Cucumber BDD tests
    - Report failure summary on test failure
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5_

- [x] 12. Integration testing and final wiring
  - [x] 12.1 Write integration tests for PostgreSQL operations
    - Test Author CRUD, Category CRUD, Article CRUD with Testcontainers PostgreSQL
    - Test FK constraint enforcement (author_id, category_id)
    - Test full-text search with tsvector
    - Verify Flyway migrations apply correctly
    - _Requirements: 1.1, 2.1, 3.1, 3.6, 3.7, 5.1, 9.1, 9.2_

  - [x] 12.2 Write integration tests for MongoDB operations
    - Test engagement record upsert for page views, read time, interactions
    - Test aggregation queries for author-level metrics
    - Use Testcontainers MongoDB
    - _Requirements: 6.1, 6.2, 7.1, 7.2_

  - [x] 12.3 Write integration tests for Kafka event publishing
    - Test successful event delivery to Kafka topic
    - Test retry behavior when broker is unavailable
    - Test dead-letter store fallback
    - Use Testcontainers Kafka
    - _Requirements: 8.1, 8.3, 8.4, 8.5_

  - [x] 12.4 Write Cucumber BDD feature files for key user journeys
    - Scenario: Author creates article → submits for review → editor publishes → event emitted
    - Scenario: Reader searches for articles → finds published content
    - Scenario: Analytics recording and retrieval flow
    - Scenario: Error scenarios (invalid transitions, duplicate emails, deletion guards)
    - _Requirements: 4.2, 4.3, 5.1, 6.1, 7.1, 8.1_

- [x] 13. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document using jqwik
- Unit tests validate specific examples and edge cases using JUnit 5
- Integration tests use Testcontainers to run against real PostgreSQL, MongoDB, and Kafka instances
- All domain logic is in Kotlin; infrastructure adapters may use Java where Spring Boot integration is more idiomatic
- The hexagonal architecture ensures domain ports are implemented before controllers, keeping the dependency direction correct

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["1.3", "1.4", "1.5"] },
    { "id": 3, "tasks": ["2.1", "2.3", "2.5", "2.8"] },
    { "id": 4, "tasks": ["2.2", "2.4", "2.6", "2.7", "2.9"] },
    { "id": 5, "tasks": ["4.1", "4.2"] },
    { "id": 6, "tasks": ["4.3", "4.4", "4.5", "5.1", "5.2"] },
    { "id": 7, "tasks": ["5.3", "5.4", "5.5"] },
    { "id": 8, "tasks": ["6.1", "6.4", "6.6", "6.9", "6.11", "6.13"] },
    { "id": 9, "tasks": ["6.2", "6.3", "6.5", "6.7", "6.8", "6.10", "6.12"] },
    { "id": 10, "tasks": ["8.1"] },
    { "id": 11, "tasks": ["8.2", "8.3"] },
    { "id": 12, "tasks": ["9.1", "9.2"] },
    { "id": 13, "tasks": ["9.3", "9.4", "9.5", "9.6", "9.7", "9.8"] },
    { "id": 14, "tasks": ["9.9", "9.10", "9.11"] },
    { "id": 15, "tasks": ["11.1", "11.2", "11.3"] },
    { "id": 16, "tasks": ["12.1", "12.2", "12.3", "12.4"] }
  ]
}
```
