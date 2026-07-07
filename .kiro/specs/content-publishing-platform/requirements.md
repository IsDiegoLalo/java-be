# Requirements Document

## Introduction

This document specifies the requirements for a Content Publishing Platform microservice. The platform enables authors to create, manage, and publish articles through an editorial workflow (draft → review → published), organized by categories and tags. It provides full-text search capabilities, engagement analytics (page views, read time, interactions), and event-driven notifications triggered on publication. The system uses PostgreSQL for relational data (authors, articles, categories) and MongoDB for high-write engagement analytics.

## Glossary

- **Platform**: The Content Publishing Platform microservice application
- **Author**: A registered user who creates and manages articles
- **Article**: A content entity with title, body, tags, category, and publication status that progresses through an editorial workflow
- **Category**: A classification grouping for articles (e.g., Technology, Science, Opinion)
- **Editorial_Workflow**: The state machine governing article lifecycle: draft → review → published
- **Analytics_Engine**: The subsystem responsible for recording and aggregating engagement metrics in MongoDB
- **Search_Engine**: The subsystem providing full-text search over articles using PostgreSQL tsvector/tsquery
- **Event_Publisher**: The subsystem responsible for publishing domain events to Kafka
- **Notification_Service**: A downstream consumer that reacts to publish events (out of scope for this microservice, but events must be emitted)
- **Engagement_Record**: A MongoDB document capturing page views, read time, and interactions for a single article
- **Tag**: A free-form label attached to an article for flexible categorization

## Requirements

### Requirement 1: Author Management

**User Story:** As an administrator, I want to manage author records, so that only registered authors can create articles.

#### Acceptance Criteria

1. THE Platform SHALL provide CRUD operations (create, read, update, delete) for Author entities
2. WHEN an Author is created, THE Platform SHALL validate that the email address is unique and conforms to RFC 5322 format with a maximum length of 255 characters
3. IF an Author has one or more articles in any status, THEN THE Platform SHALL reject deletion of that Author and return an error indicating the Author has associated articles
4. THE Platform SHALL store Author entities in PostgreSQL with fields: id, name, email, bio, and created_at
5. WHEN an Author is created or updated, THE Platform SHALL require name (1 to 100 characters) and email as mandatory fields, and treat bio as optional (maximum 500 characters)
6. IF an Author is created or updated with an email that already belongs to another Author, THEN THE Platform SHALL return an error indicating the email address is already in use

### Requirement 2: Category Management

**User Story:** As an editor, I want to manage article categories, so that articles can be organized into meaningful groups.

#### Acceptance Criteria

1. THE Platform SHALL provide CRUD operations (create, read, update, delete) for Category entities
2. WHEN a Category is created or updated, THE Platform SHALL validate that the category name is unique (case-insensitive), between 2 and 100 characters in length, and not blank
3. IF a Category deletion is requested and one or more articles are assigned to that Category, THEN THE Platform SHALL reject the deletion and return an error indicating the Category is in use
4. THE Platform SHALL store Category entities in PostgreSQL with fields: id, name, description (max 500 characters, optional), and slug
5. WHEN a Category is created or updated, THE Platform SHALL auto-generate the slug as a URL-safe, lowercase, hyphen-separated transliteration of the category name, and validate that the resulting slug is unique

### Requirement 3: Article CRUD Operations

**User Story:** As an author, I want to create and manage articles, so that I can produce content for publication.

#### Acceptance Criteria

1. THE Platform SHALL provide CRUD operations (create, read, update, list, delete) for Article entities
2. WHEN an Article is created, THE Platform SHALL assign the initial status of "draft"
3. THE Platform SHALL store Article entities in PostgreSQL with fields: id, title, body, summary, author_id, category_id, tags, status, created_at, updated_at, and published_at
4. WHEN an Article is listed, THE Platform SHALL support pagination with a configurable page size between 1 and 100 (default 20) and a page number starting from 0
5. WHEN an Article is listed, THE Platform SHALL support filtering by author, category, status, and tags
6. THE Platform SHALL enforce a foreign key relationship between Article and Author entities
7. THE Platform SHALL enforce a foreign key relationship between Article and Category entities
8. WHEN an Article is created, THE Platform SHALL validate that title is present with a maximum length of 255 characters, body is present, summary has a maximum length of 500 characters, and tags contains no more than 10 entries
9. IF an Article is created or updated with an author_id or category_id that does not reference an existing entity, THEN THE Platform SHALL reject the request with an error indicating which referenced entity was not found
10. IF an Article in "published" status is requested for deletion, THEN THE Platform SHALL reject the deletion with an error indicating that published articles cannot be deleted

### Requirement 4: Editorial Workflow

**User Story:** As an editor, I want articles to progress through a defined editorial workflow, so that content quality is ensured before publication.

#### Acceptance Criteria

1. THE Editorial_Workflow SHALL define exactly three states: draft, review, and published
2. WHILE an Article is in "draft" status, WHEN a status transition is requested, THE Platform SHALL allow transition only to "review"
3. WHILE an Article is in "review" status, WHEN a status transition is requested, THE Platform SHALL allow transition to "published" or back to "draft"
4. WHILE an Article is in "published" status, WHEN a status transition is requested, THE Platform SHALL reject the request with an error indicating that published articles cannot change status
5. IF an invalid status transition is requested, THEN THE Platform SHALL return an error indicating the current state and the allowed transitions from that state
6. WHEN an Article transitions to "published", THE Platform SHALL set the published_at timestamp to the current UTC time in ISO 8601 format
7. WHEN an Article transitions to any new status, THE Platform SHALL update the updated_at timestamp to the current UTC time
8. IF a status transition is requested for an Article that does not exist, THEN THE Platform SHALL return an error indicating that the article was not found

### Requirement 5: Full-Text Search

**User Story:** As a reader, I want to search articles by keywords, so that I can find relevant content quickly.

#### Acceptance Criteria

1. THE Search_Engine SHALL provide full-text search over Article title and body fields using PostgreSQL tsvector indexing
2. WHEN a search query is submitted, THE Search_Engine SHALL return articles ranked by relevance score
3. WHEN a search query is submitted, THE Search_Engine SHALL support pagination of search results with a configurable page size between 1 and 100 (default 20) and a page number starting from 0
4. THE Search_Engine SHALL only return articles with "published" status in search results
5. WHEN a search query yields no matches, THE Search_Engine SHALL return an empty result set with zero total count
6. IF a search query is empty or blank, THEN THE Search_Engine SHALL return an error indicating that a non-empty search term is required
7. THE Search_Engine SHALL accept search queries with a maximum length of 200 characters

### Requirement 6: Engagement Analytics Recording

**User Story:** As a product owner, I want to track article engagement metrics, so that content performance can be measured.

#### Acceptance Criteria

1. WHEN a page view event is received, THE Analytics_Engine SHALL create or update the Engagement_Record for the specified article in MongoDB
2. THE Analytics_Engine SHALL store the following metrics per article: total page views, average read time in seconds, and individual interaction counts for likes, shares, and comments
3. WHEN a read time metric is recorded with a value between 1 and 3600 seconds, THE Analytics_Engine SHALL update the running average for that article
4. WHEN an interaction event is received with type "like", "share", or "comment", THE Analytics_Engine SHALL increment the corresponding interaction count for the specified article
5. THE Analytics_Engine SHALL process engagement write operations asynchronously so that the main article API responses are returned within 200 milliseconds regardless of analytics load
6. IF a page view, read time, or interaction event references an article ID that does not exist, THEN THE Analytics_Engine SHALL discard the event and log a warning
7. IF a read time metric is received with a value less than 1 or greater than 3600 seconds, THEN THE Analytics_Engine SHALL discard the event without updating the Engagement_Record

### Requirement 7: Engagement Analytics Retrieval

**User Story:** As an author, I want to view engagement analytics for my articles, so that I can understand content performance.

#### Acceptance Criteria

1. WHEN analytics are requested for a single article, THE Analytics_Engine SHALL return the complete Engagement_Record including page views, average read time in seconds, and interaction count
2. WHEN analytics are requested for an author, THE Analytics_Engine SHALL return aggregated metrics across all articles by that author, calculated as: total page views (sum), overall average read time in seconds (weighted average by page views), and total interaction count (sum)
3. IF analytics are requested for an article with no recorded engagement, THEN THE Analytics_Engine SHALL return zeroed metrics (0 page views, 0.0 average read time, 0 interactions) rather than an error
4. IF analytics are requested for an article that does not exist, THEN THE Analytics_Engine SHALL return an error indicating the article was not found
5. IF analytics are requested for an author who has no articles, THEN THE Analytics_Engine SHALL return zeroed aggregated metrics rather than an error

### Requirement 8: Event-Driven Publish Notifications

**User Story:** As a system integrator, I want publish events emitted to Kafka, so that downstream services can react to new content.

#### Acceptance Criteria

1. WHEN an Article transitions to "published" status, THE Event_Publisher SHALL emit a publish event to a configured Kafka topic within 5 seconds of the transition
2. THE Event_Publisher SHALL include the article id, title, author id, category, tags, and published_at timestamp in the event payload
3. IF the Kafka broker is unavailable, THEN THE Event_Publisher SHALL retry event delivery with exponential backoff starting at 1 second and doubling per attempt, up to a maximum of three attempts
4. IF all retry attempts fail, THEN THE Event_Publisher SHALL log the failure at ERROR level and store the failed event in a persistent dead-letter store, retaining it for a minimum of 7 days for later reprocessing
5. THE Event_Publisher SHALL guarantee at-least-once delivery semantics, ensuring no publish event is silently lost without either successful delivery or storage in the dead-letter store

### Requirement 9: Database Schema Migration

**User Story:** As a developer, I want database schemas managed through versioned migrations, so that schema changes are reproducible and auditable.

#### Acceptance Criteria

1. THE Platform SHALL use Flyway to manage all PostgreSQL schema changes as versioned migrations
2. WHEN the application starts, THE Platform SHALL automatically apply any pending Flyway migrations in ascending version order
3. THE Platform SHALL store migration scripts in a dedicated directory following Flyway naming conventions (V{version}__{description}.sql)
4. IF a Flyway migration fails during application startup, THEN THE Platform SHALL prevent the application from accepting requests and log the migration error at ERROR level including the failed script version
5. THE Platform SHALL record all applied migrations in Flyway's schema history table, capturing the version, description, execution timestamp, and success status for auditability

### Requirement 10: API Response Consistency

**User Story:** As an API consumer, I want consistent response formats, so that client integration is predictable.

#### Acceptance Criteria

1. THE Platform SHALL return all successful responses with appropriate HTTP status codes (200 for retrieval, 201 for creation, 204 for deletion)
2. THE Platform SHALL return all error responses following RFC 7807 Problem Details format with fields: type, title, status, detail, and instance
3. WHEN input validation fails, THE Platform SHALL return HTTP 422 with specific field-level error descriptions in the Problem Details response
4. THE Platform SHALL include pagination metadata (page, size, totalElements, totalPages) in all list responses
5. THE Platform SHALL return HTTP 404 with a Problem Details response when a requested resource is not found
6. THE Platform SHALL return HTTP 409 with a Problem Details response when a conflict occurs (e.g., duplicate email, category in use)

### Requirement 11: Containerization

**User Story:** As a DevOps engineer, I want the application containerized with Docker Compose, so that the full stack can be started with a single command.

#### Acceptance Criteria

1. THE Platform SHALL provide a Dockerfile that builds a container image using multi-stage builds, where the final stage uses a JRE-only base image and runs as a non-root user
2. THE Platform SHALL provide a Docker Compose configuration that starts PostgreSQL, MongoDB, Kafka (with Zookeeper), and the application service with a single `docker compose up` command
3. WHEN Docker Compose is started, THE Platform SHALL verify dependent service health using connection checks (PostgreSQL accepts connections, MongoDB responds to ping, Kafka broker is reachable) and wait up to 60 seconds before accepting requests
4. IF a dependent service fails to become healthy within 60 seconds, THEN THE Platform SHALL prevent the application service from starting and log an error indicating which service is unavailable
5. THE Platform SHALL expose configuration through environment variables for database URLs, Kafka broker addresses, and server port, with working default values provided in the Docker Compose file for local development
6. THE Platform SHALL define named volumes in the Docker Compose configuration for PostgreSQL and MongoDB data persistence across container restarts

### Requirement 12: Article Serialization Round-Trip

**User Story:** As a developer, I want article JSON serialization to be lossless, so that API consumers receive accurate data representations.

#### Acceptance Criteria

1. THE Platform SHALL serialize Article entities to JSON including all persisted fields (id, title, body, summary, author_id, category_id, tags, status, created_at, updated_at, and published_at) and deserialize JSON back to Article entities preserving field-by-field equality for all non-null fields
2. THE Platform SHALL serialize and deserialize the round-trip such that for every Article field, the deserialized value equals the original value when compared by type-appropriate equality (string equality for text fields, element-order equality for tags, and millisecond-precision equality for date-time fields)
3. THE Platform SHALL handle all date-time fields using ISO 8601 format with millisecond precision in UTC timezone (pattern: yyyy-MM-dd'T'HH:mm:ss.SSS'Z') during serialization
4. IF an Article field is null (e.g., published_at for draft articles), THEN THE Platform SHALL serialize the field as a JSON null value and deserialize it back as null, rather than omitting the field from the JSON output
5. IF the tags field is an empty collection, THEN THE Platform SHALL serialize it as an empty JSON array and deserialize it back as an empty collection, rather than null

### Requirement 13: CI/CD Pipeline

**User Story:** As a developer, I want automated build and test execution on every push, so that code quality is continuously verified.

#### Acceptance Criteria

1. THE Platform SHALL provide a GitHub Actions workflow that builds the project on every push and pull request to the main branch
2. WHEN the CI pipeline runs, THE Platform SHALL execute unit tests, integration tests (using Testcontainers for PostgreSQL, MongoDB, and Kafka), and Cucumber BDD tests in sequence
3. IF any test fails, THEN THE Platform SHALL mark the pipeline as failed and report the failure summary including the test name and assertion message
4. THE Platform SHALL use Gradle (Kotlin DSL) as the build tool with JDK 21
5. THE Platform SHALL cache Gradle dependencies in the CI pipeline to reduce build times
