---
inclusion: always
---

# Design Patterns Guidelines

Apply design patterns where they naturally fit. Do not force patterns where they add unnecessary complexity.

## Creational Patterns

### Builder Pattern
- Use for objects with many optional parameters
- In Kotlin, prefer data classes with default parameters over builders when possible
- In Java, use the Builder pattern for immutable objects with 4+ constructor parameters

### Factory Method / Abstract Factory
- Use when object creation logic is complex or varies by context
- Prefer factory methods over direct constructor calls when the instantiation type may change
- Use Spring's `@Bean` methods as factory methods in configuration classes

### Singleton
- Avoid manual singletons; use Spring's default singleton scope for beans
- In Kotlin, use `object` declarations for true singletons outside Spring context

## Structural Patterns

### Adapter Pattern
- Use to integrate with external APIs or legacy code
- Create adapter classes to translate between external DTOs and internal domain models

### Decorator Pattern
- Use to add behavior to objects dynamically without modifying their class
- Good for cross-cutting concerns like logging, caching, or validation wrappers

### Facade Pattern
- Use to simplify complex subsystems behind a unified interface
- Service layer classes often act as facades over repositories and domain logic

## Behavioral Patterns

### Strategy Pattern
- Use when an algorithm or behavior varies and should be selected at runtime
- Define strategies as interfaces with multiple implementations
- Inject the appropriate strategy via DI

### Observer Pattern
- Use for event-driven communication between components
- Leverage Spring's `ApplicationEventPublisher` and `@EventListener`

### Template Method Pattern
- Use when a process has fixed steps but some steps vary
- Define the skeleton in an abstract class; let subclasses implement variable steps

### Command Pattern
- Use to encapsulate requests as objects
- Good for undo/redo, queuing, or audit logging scenarios

## When to Apply Patterns

- Pattern usage must solve a real problem, not be applied speculatively
- If a pattern adds more code than the problem warrants, skip it
- Document the pattern name in a comment when applying one (e.g., `// Strategy Pattern`)
- Prefer simpler solutions first; reach for patterns when complexity demands them
