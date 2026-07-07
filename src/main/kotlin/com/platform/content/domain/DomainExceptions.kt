package com.platform.content.domain

import com.platform.content.domain.model.ArticleStatus
import java.util.UUID

/**
 * Base sealed class for all domain exceptions.
 * Each subtype represents a single, well-defined error condition (SRP).
 * Sealed hierarchy enables exhaustive matching in exception handlers (OCP).
 */
sealed class DomainException(message: String) : RuntimeException(message)

/**
 * Thrown when a requested entity cannot be found by its identifier.
 */
data class EntityNotFoundException(
    val entityType: String,
    val entityId: UUID
) : DomainException("$entityType with id $entityId not found")

/**
 * Thrown when an operation conflicts with existing state
 * (e.g., duplicate email, category in use).
 */
data class ConflictException(
    val entityType: String,
    val conflictReason: String
) : DomainException("$entityType conflict: $conflictReason")

/**
 * Thrown when an article status transition violates the editorial workflow rules.
 */
data class InvalidTransitionException(
    val currentState: ArticleStatus,
    val targetState: ArticleStatus,
    val allowedTransitions: Set<ArticleStatus>
) : DomainException(
    "Cannot transition from ${currentState.value} to ${targetState.value}. " +
        "Allowed: ${allowedTransitions.map { it.value }}"
)

/**
 * Thrown when domain-level validation fails beyond what Bean Validation covers.
 */
data class ValidationException(
    val fieldErrors: List<FieldError>
) : DomainException("Validation failed: ${fieldErrors.joinToString { "${it.field}: ${it.message}" }}")

/**
 * Thrown when event publishing to Kafka fails after all retry attempts.
 */
data class EventPublishingException(
    val eventId: UUID,
    val reason: String
) : DomainException("Failed to publish event $eventId: $reason")

/**
 * Represents a single field-level validation error.
 */
data class FieldError(
    val field: String,
    val message: String
)
