package com.platform.content.domain.workflow

import com.platform.content.domain.model.ArticleStatus

/**
 * Represents the outcome of a workflow transition validation.
 * Sealed class enables exhaustive matching in `when` expressions.
 */
sealed class TransitionResult {
    /** The requested transition is valid per the editorial workflow rules. */
    data object Valid : TransitionResult()

    /** The requested transition is invalid; includes the current state and allowed transitions. */
    data class Invalid(
        val currentState: ArticleStatus,
        val allowed: Set<ArticleStatus>
    ) : TransitionResult()
}
