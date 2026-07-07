package com.platform.content.domain.workflow

import com.platform.content.domain.model.ArticleStatus
import org.springframework.stereotype.Component

/**
 * Implements the editorial workflow state machine.
 *
 * Transition rules:
 * - Draft → Review (only)
 * - Review → Published or Draft
 * - Published → none (terminal state)
 */
@Component
class EditorialWorkflowValidator : WorkflowTransitionValidator {

    private val transitions: Map<ArticleStatus, Set<ArticleStatus>> = mapOf(
        ArticleStatus.Draft to setOf(ArticleStatus.Review),
        ArticleStatus.Review to setOf(ArticleStatus.Published, ArticleStatus.Draft),
        ArticleStatus.Published to emptySet()
    )

    override fun allowedTransitions(from: ArticleStatus): Set<ArticleStatus> =
        transitions[from] ?: emptySet()

    override fun validate(from: ArticleStatus, to: ArticleStatus): TransitionResult {
        val allowed = allowedTransitions(from)
        return if (to in allowed) {
            TransitionResult.Valid
        } else {
            TransitionResult.Invalid(currentState = from, allowed = allowed)
        }
    }
}
