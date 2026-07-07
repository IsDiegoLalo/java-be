---
inclusion: always
---

# Java & Kotlin Best Practices

## General Coding Standards

### Naming Conventions
- Classes: PascalCase (e.g., `UserService`, `OrderRepository`)
- Methods/functions: camelCase (e.g., `findById`, `calculateTotal`)
- Constants: UPPER_SNAKE_CASE (e.g., `MAX_RETRY_COUNT`)
- Packages: lowercase dot-separated (e.g., `com.example.service`)

### Code Organization
- Follow a layered architecture: controller → service → repository
- Group by feature/domain, not by technical layer, for larger projects
- Keep methods short (ideally under 20 lines)
- Limit class size to a single responsibility

### Error Handling
- Use specific exception types, not generic `Exception`
- Create custom exceptions for domain-specific errors
- Handle exceptions at the appropriate layer (don't catch too early)
- Use `@ControllerAdvice` / `@ExceptionHandler` for centralized REST error handling
- In Kotlin, prefer `Result` type or sealed classes for expected failure cases

### Immutability
- Prefer immutable objects (final fields in Java, val in Kotlin)
- Use `data class` in Kotlin for value objects
- Use `record` in Java 17+ for simple immutable data carriers
- Return unmodifiable collections from public APIs

## Java-Specific

- Use `Optional` for return types that may be absent; never for parameters or fields
- Prefer streams for collection transformations when readability improves
- Use `var` (local variable type inference) for obvious types only
- Annotate nullable parameters with `@Nullable` / `@NonNull`
- Use `sealed` classes (Java 17+) when modeling closed type hierarchies

## Kotlin-Specific

- Use `val` by default; only use `var` when mutation is necessary
- Leverage null safety: avoid `!!` operator; use safe calls `?.` and elvis `?:`
- Use extension functions to add behavior without inheritance
- Use `when` expressions for exhaustive matching on sealed types
- Use coroutines for async operations instead of raw threads
- Prefer `data class` for DTOs and value objects
- Use scope functions (`let`, `apply`, `run`, `also`, `with`) idiomatically

## Testing

- Write unit tests for all business logic
- Use JUnit 5 for Java tests, JUnit 5 or Kotest for Kotlin tests
- Use Mockito or MockK for mocking dependencies
- Follow the Arrange-Act-Assert pattern
- Name tests descriptively: `should_returnEmpty_when_userNotFound`
- Aim for meaningful coverage of edge cases, not arbitrary coverage percentages

## Documentation

- Add KDoc/Javadoc to public APIs (classes, interfaces, public methods)
- Document "why" not "what" in inline comments
- Keep README updated with setup and architecture decisions
