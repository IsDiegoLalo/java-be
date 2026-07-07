# Content Publishing Platform

A Spring Boot 3.x microservice built with Kotlin and Java 21 that provides a complete content management and publishing workflow. The system manages authors, categories, and articles through an editorial lifecycle (draft → review → published), provides full-text search, tracks engagement analytics, and emits domain events via Kafka.

## Architecture

```
Hexagonal Architecture (Ports & Adapters)

┌─────────────────────────────────────────────────────────┐
│                    REST API Layer                         │
│  Controllers · DTOs · GlobalExceptionHandler (RFC 7807)  │
├─────────────────────────────────────────────────────────┤
│                  Application Services                     │
│  AuthorService · ArticleService · WorkflowService        │
│  SearchService · EngagementService                       │
├─────────────────────────────────────────────────────────┤
│                    Domain Layer                           │
│  Entities · Value Objects · Ports · Workflow Engine       │
├─────────────────────────────────────────────────────────┤
│               Infrastructure Adapters                    │
│  PostgreSQL (JPA) · MongoDB · Kafka · Full-Text Search   │
└─────────────────────────────────────────────────────────┘
```

**Tech Stack:**
- Kotlin + Java 21
- Spring Boot 3.x (Web, Data JPA, Data MongoDB, Kafka)
- PostgreSQL 16 (articles, authors, categories, outbox)
- MongoDB 7 (engagement analytics)
- Apache Kafka (domain events via outbox pattern)
- Flyway (database migrations)
- jqwik (property-based testing)
- Testcontainers (integration tests)
- Cucumber (BDD tests)

## Prerequisites

- JDK 21 (Temurin recommended)
- Docker & Docker Compose
- (Optional) kubectl + AWS CLI for EKS deployment

## Running Locally

### Option 1: Docker Compose (recommended)

Starts all services (app + PostgreSQL + MongoDB + Kafka):

```bash
docker compose up --build
```

The application will be available at `http://localhost:8080`.

### Option 2: Run the app outside Docker

Start only the infrastructure:

```bash
docker compose up postgres mongodb zookeeper kafka
```

Then run the Spring Boot app:

```bash
./gradlew bootRun --no-daemon
```

### Option 3: Gradle only (unit tests)

To run unit and property-based tests (no Docker needed):

```bash
./gradlew test --no-daemon \
  --tests "com.platform.content.domain.*" \
  --tests "com.platform.content.application.*" \
  --tests "com.platform.content.api.*" \
  --tests "com.platform.content.infrastructure.messaging.KafkaArticleEventPublisherTest" \
  --tests "com.platform.content.infrastructure.messaging.OutboxProcessorTest" \
  --tests "com.platform.content.infrastructure.messaging.EventPayloadCompletenessPropertyTest"
```

To run all tests including integration (requires Docker):

```bash
./gradlew test --no-daemon
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/content_platform` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `platform_user` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | `platform_pass` | PostgreSQL password |
| `SPRING_DATA_MONGODB_URI` | `mongodb://mongo_user:mongo_pass@localhost:27017/engagement?authSource=admin` | MongoDB connection URI |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `SERVER_PORT` | `8080` | Application port |
| `SPRING_PROFILES_ACTIVE` | `default` | Active Spring profile (`docker` for containers) |

## API Endpoints

### Authors

| Method | Path | Description | Status Codes |
|--------|------|-------------|--------------|
| `POST` | `/authors` | Create a new author | 201, 409, 422 |
| `GET` | `/authors/{id}` | Get author by ID | 200, 404 |
| `PUT` | `/authors/{id}` | Update an author | 200, 404, 409, 422 |
| `DELETE` | `/authors/{id}` | Delete an author | 204, 404, 409 |

**Create/Update Author Request:**
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "bio": "A passionate writer"
}
```

### Categories

| Method | Path | Description | Status Codes |
|--------|------|-------------|--------------|
| `POST` | `/categories` | Create a new category | 201, 409, 422 |
| `GET` | `/categories/{id}` | Get category by ID | 200, 404 |
| `PUT` | `/categories/{id}` | Update a category | 200, 404, 409, 422 |
| `DELETE` | `/categories/{id}` | Delete a category | 204, 404, 409 |

**Create/Update Category Request:**
```json
{
  "name": "Technology",
  "description": "Articles about technology"
}
```

### Articles

| Method | Path | Description | Status Codes |
|--------|------|-------------|--------------|
| `POST` | `/articles` | Create a new article (starts as draft) | 201, 404, 422 |
| `GET` | `/articles/{id}` | Get article by ID | 200, 404 |
| `PUT` | `/articles/{id}` | Update an article | 200, 404, 422 |
| `DELETE` | `/articles/{id}` | Delete an article (not if published) | 204, 404, 409 |
| `GET` | `/articles` | List articles with filters | 200 |
| `PUT` | `/articles/{id}/status` | Transition article status | 200, 404, 422 |

**Create Article Request:**
```json
{
  "title": "Getting Started with Kotlin",
  "body": "Full article content here...",
  "summary": "A brief introduction to Kotlin",
  "authorId": "uuid-here",
  "categoryId": "uuid-here",
  "tags": ["kotlin", "programming"]
}
```

**Transition Status Request:**
```json
{
  "targetStatus": "review"
}
```

**List Articles Query Parameters:**
- `authorId` (UUID, optional) — filter by author
- `categoryId` (UUID, optional) — filter by category
- `status` (string, optional) — filter by status: `draft`, `review`, `published`
- `tags` (list, optional) — filter by tags
- `page` (int, default 0) — page number
- `size` (int, default 20, max 100) — page size

### Search

| Method | Path | Description | Status Codes |
|--------|------|-------------|--------------|
| `GET` | `/articles/search?q={query}` | Full-text search (published only) | 200, 422 |

**Query Parameters:**
- `q` (string, required) — search term (1-200 chars, non-blank)
- `page` (int, default 0)
- `size` (int, default 20)

### Analytics

| Method | Path | Description | Status Codes |
|--------|------|-------------|--------------|
| `GET` | `/analytics/articles/{id}` | Get article engagement metrics | 200, 404 |
| `GET` | `/analytics/authors/{id}` | Get author aggregated metrics | 200 |
| `POST` | `/analytics/events/page-view` | Record a page view | 202 |
| `POST` | `/analytics/events/read-time` | Record read time | 202 |
| `POST` | `/analytics/events/interaction` | Record interaction | 202 |

**Page View Request:**
```json
{ "articleId": "uuid-here" }
```

**Read Time Request:**
```json
{ "articleId": "uuid-here", "seconds": 120 }
```

**Interaction Request:**
```json
{ "articleId": "uuid-here", "type": "LIKE" }
```
Types: `LIKE`, `SHARE`, `COMMENT`

### Health Check

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/actuator/health` | Application health status |

## Editorial Workflow

Articles follow a strict state machine:

```
            ┌──────────┐
            │  DRAFT   │
            └────┬─────┘
                 │ submit for review
                 ▼
            ┌──────────┐
     ┌──────│  REVIEW  │──────┐
     │      └──────────┘      │
     │ reject (back to draft) │ approve (publish)
     ▼                        ▼
┌──────────┐           ┌───────────┐
│  DRAFT   │           │ PUBLISHED │ (terminal state)
└──────────┘           └───────────┘
```

- **Draft → Review**: Author submits article for editorial review
- **Review → Published**: Editor approves and publishes (triggers Kafka event)
- **Review → Draft**: Editor sends back for revisions
- **Published → (none)**: Terminal state, no further transitions allowed

## Error Responses (RFC 7807)

All errors return Problem Details format:

```json
{
  "type": "/problems/not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Article with id 123e4567-e89b-12d3-a456-426614174000 not found",
  "instance": "/articles/123e4567-e89b-12d3-a456-426614174000"
}
```

Validation errors include field-level details:

```json
{
  "type": "/problems/validation-error",
  "title": "Validation Error",
  "status": 422,
  "detail": "One or more fields failed validation",
  "instance": "/authors",
  "fieldErrors": [
    { "field": "email", "message": "must be a valid email address" },
    { "field": "name", "message": "must not be blank" }
  ]
}
```

## Pagination Response Format

All list endpoints return:

```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

## Deploying to AWS EKS

### 1. Build and Push Docker Image

```bash
# Authenticate with ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# Build and tag
docker build -t content-platform .
docker tag content-platform:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/content-platform:latest

# Push
docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/content-platform:latest
```

### 2. Create EKS Cluster (if not exists)

```bash
eksctl create cluster \
  --name content-platform-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 3
```

### 3. Create Kubernetes Secrets

```bash
kubectl create secret generic content-platform-secrets \
  --from-literal=SPRING_DATASOURCE_URL=jdbc:postgresql://<RDS_ENDPOINT>:5432/content_platform \
  --from-literal=SPRING_DATASOURCE_USERNAME=platform_user \
  --from-literal=SPRING_DATASOURCE_PASSWORD=<SECURE_PASSWORD> \
  --from-literal=SPRING_DATA_MONGODB_URI=mongodb://<DOCDB_ENDPOINT>:27017/engagement \
  --from-literal=SPRING_KAFKA_BOOTSTRAP_SERVERS=<MSK_BOOTSTRAP_SERVERS>
```

### 4. Apply Kubernetes Manifests

Create `k8s/deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: content-platform
  labels:
    app: content-platform
spec:
  replicas: 2
  selector:
    matchLabels:
      app: content-platform
  template:
    metadata:
      labels:
        app: content-platform
    spec:
      containers:
        - name: content-platform
          image: <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/content-platform:latest
          ports:
            - containerPort: 8080
          envFrom:
            - secretRef:
                name: content-platform-secrets
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "docker"
            - name: SERVER_PORT
              value: "8080"
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: content-platform-service
spec:
  type: LoadBalancer
  ports:
    - port: 80
      targetPort: 8080
      protocol: TCP
  selector:
    app: content-platform
```

Apply:

```bash
kubectl apply -f k8s/deployment.yaml
```

### 5. Verify Deployment

```bash
kubectl get pods -l app=content-platform
kubectl get service content-platform-service
```

### AWS Managed Services (recommended for production)

| Service | AWS Equivalent |
|---------|---------------|
| PostgreSQL | Amazon RDS for PostgreSQL |
| MongoDB | Amazon DocumentDB |
| Kafka | Amazon MSK (Managed Streaming for Kafka) |

### Production Checklist

- [ ] Use AWS Secrets Manager or SSM Parameter Store for credentials
- [ ] Configure RDS with Multi-AZ for high availability
- [ ] Set up CloudWatch alarms for CPU, memory, and error rates
- [ ] Enable ALB Ingress Controller for HTTPS termination
- [ ] Configure Horizontal Pod Autoscaler (HPA)
- [ ] Set up network policies and security groups
- [ ] Enable container image scanning in ECR
- [ ] Configure backup policies for RDS and DocumentDB

## Project Structure

```
src/main/kotlin/com/platform/content/
├── api/                          # REST controllers, DTOs, error handling
│   ├── controller/               # AuthorController, ArticleController, etc.
│   ├── dto/                      # Request/Response DTOs with validation
│   └── error/                    # GlobalExceptionHandler (RFC 7807)
├── application/                  # Application services (use cases)
│   ├── author/                   # AuthorService, AuthorValidator
│   ├── article/                  # ArticleService, WorkflowService, Validator
│   ├── category/                 # CategoryService, Validator, SlugGenerator
│   ├── search/                   # SearchService
│   └── analytics/                # EngagementService
├── domain/                       # Domain entities, ports, exceptions
│   ├── model/                    # Article, Author, Category, Engagement
│   ├── port/                     # Repository interfaces, search, events
│   └── workflow/                 # Editorial workflow state machine
├── infrastructure/               # Adapters (database, messaging)
│   ├── persistence/              # JPA entities, Spring Data repos
│   ├── analytics/                # MongoDB adapters
│   ├── messaging/                # Kafka publisher, outbox processor
│   ├── search/                   # PostgreSQL tsvector adapter
│   └── config/                   # Spring configuration
└── ContentPublishingApplication.kt
```

## Testing

The project follows a test pyramid:

| Layer | Framework | Count | Docker Required |
|-------|-----------|-------|-----------------|
| Unit tests | JUnit 5 + MockK | ~80 | No |
| Property tests | jqwik | ~20 properties x 100 tries | No |
| Integration tests | Testcontainers | ~40 | Yes |
| BDD tests | Cucumber | ~30 scenarios | Yes |

Run only unit + property tests (no Docker):

```bash
./gradlew test --no-daemon --tests "com.platform.content.domain.*" \
  --tests "com.platform.content.application.*" \
  --tests "com.platform.content.api.*" \
  --tests "com.platform.content.infrastructure.messaging.*Test"
```

Run full suite (Docker required):

```bash
./gradlew test --no-daemon
```

## License

This project is for educational/evaluation purposes.
