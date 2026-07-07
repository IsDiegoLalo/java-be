---
inclusion: always
---

# SOLID Design Principles

All Java and Kotlin code in this project MUST adhere to the SOLID principles:

## Single Responsibility Principle (SRP)
- Each class should have only one reason to change
- Keep classes focused on a single concern
- Extract responsibilities into separate classes when a class does too much
- Services, repositories, controllers, and utilities should each handle one domain concept

## Open/Closed Principle (OCP)
- Classes should be open for extension but closed for modification
- Use interfaces and abstract classes to allow behavior extension without changing existing code
- Prefer composition and strategy patterns over modifying existing classes
- Use sealed classes/interfaces in Kotlin when the set of subtypes is known

## Liskov Substitution Principle (LSP)
- Subtypes must be substitutable for their base types without altering correctness
- Do not override methods in ways that violate the base class contract
- Preconditions cannot be strengthened in a subtype
- Postconditions cannot be weakened in a subtype
- Prefer composition over inheritance when LSP is difficult to maintain

## Interface Segregation Principle (ISP)
- Clients should not be forced to depend on interfaces they do not use
- Keep interfaces small and focused
- Split large interfaces into smaller, role-specific ones
- In Kotlin, leverage default interface methods and delegation

## Dependency Inversion Principle (DIP)
- High-level modules should not depend on low-level modules; both should depend on abstractions
- Use dependency injection (constructor injection preferred)
- Program to interfaces, not implementations
- Use Spring's `@Autowired` or constructor injection for DI containers
