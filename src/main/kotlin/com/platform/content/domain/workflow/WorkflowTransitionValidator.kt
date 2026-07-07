package com.platform.content.domain.workflow

import com.platform.content.domain.model.ArticleStatus

/**
 * Strategy interface for validating editorial workflow transitions.
 * Implementations encapsulate the state machine rules, allowing
 * the workflow to be extended without modifying article logic (OCP).
 */
interface WorkflowTransitionValidator {

    /**
     * Returns the set of states reachable from the given [from] state.
     */
    fun allowedTransitions(from: ArticleStatus): Set<ArticleStatus>

    /**
     * Validates whether a transition from [from] to [to] is permitted.
     *
     * @return [TransitionResult.Valid] if the transition is allowed,
     *         [TransitionResult.Invalid] with the current state and allowed targets otherwise.
     */
    fun validate(from: ArticleStatus, to: ArticleStatus): TransitionResult
}
