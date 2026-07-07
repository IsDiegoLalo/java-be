package com.platform.content.domain.workflow

import com.platform.content.domain.model.ArticleStatus
import net.jqwik.api.*
import net.jqwik.api.lifecycle.BeforeProperty
import org.junit.jupiter.api.Tag

/**
 * Property-based test for the editorial workflow state machine.
 *
 * Validates: Requirements 4.2, 4.3, 4.4, 4.5
 *
 * Verifies that for any article status and any target status, the transition
 * validator correctly allows/rejects transitions per the defined rules:
 * - Draft → Review (only)
 * - Review → Published or Draft
 * - Published → none (terminal state)
 */
@Tag("Feature: content-publishing-platform, Property 10: Editorial workflow state machine")
class EditorialWorkflowPropertyTest {

    private lateinit var validator: EditorialWorkflowValidator

    @BeforeProperty
    fun setUp() {
        validator = EditorialWorkflowValidator()
    }

    @Provide
    fun articleStatuses(): Arbitrary<ArticleStatus> =
        Arbitraries.of(ArticleStatus.Draft, ArticleStatus.Review, ArticleStatus.Published)

    /**
     * Property: For any pair of statuses, the validator returns Valid only for
     * the three allowed transitions: draft→review, review→published, review→draft.
     * All other combinations must return Invalid.
     */
    @Property(tries = 100)
    fun validTransitionsAreExactlyThreeAllowedOnes(
        @ForAll("articleStatuses") from: ArticleStatus,
        @ForAll("articleStatuses") to: ArticleStatus
    ) {
        val result = validator.validate(from, to)

        val isAllowed = when (from) {
            ArticleStatus.Draft -> to == ArticleStatus.Review
            ArticleStatus.Review -> to == ArticleStatus.Published || to == ArticleStatus.Draft
            ArticleStatus.Published -> false
        }

        if (isAllowed) {
            assert(result is TransitionResult.Valid) {
                "Expected Valid for transition ${from.value} → ${to.value}, but got $result"
            }
        } else {
            assert(result is TransitionResult.Invalid) {
                "Expected Invalid for transition ${from.value} → ${to.value}, but got $result"
            }
        }
    }

    /**
     * Property: For any rejected transition, the Invalid result contains the correct
     * current state and the set of allowed transitions from that state.
     */
    @Property(tries = 100)
    fun invalidTransitionsIncludeCorrectStateAndAllowedSet(
        @ForAll("articleStatuses") from: ArticleStatus,
        @ForAll("articleStatuses") to: ArticleStatus
    ) {
        val result = validator.validate(from, to)

        if (result is TransitionResult.Invalid) {
            // currentState must match the 'from' status
            assert(result.currentState == from) {
                "Expected currentState to be ${from.value}, but was ${result.currentState.value}"
            }

            // allowed set must match the defined transitions for 'from'
            val expectedAllowed = when (from) {
                ArticleStatus.Draft -> setOf(ArticleStatus.Review)
                ArticleStatus.Review -> setOf(ArticleStatus.Published, ArticleStatus.Draft)
                ArticleStatus.Published -> emptySet()
            }

            assert(result.allowed == expectedAllowed) {
                "Expected allowed set ${expectedAllowed.map { it.value }} for state ${from.value}, " +
                    "but got ${result.allowed.map { it.value }}"
            }
        }
    }

    /**
     * Property: Draft status allows only transition to Review.
     * Validates: Requirement 4.2
     */
    @Property(tries = 100)
    fun draftAllowsOnlyReviewTransition(
        @ForAll("articleStatuses") to: ArticleStatus
    ) {
        val result = validator.validate(ArticleStatus.Draft, to)

        if (to == ArticleStatus.Review) {
            assert(result is TransitionResult.Valid) {
                "Draft → Review should be Valid, but got $result"
            }
        } else {
            assert(result is TransitionResult.Invalid) {
                "Draft → ${to.value} should be Invalid, but got $result"
            }
        }
    }

    /**
     * Property: Review status allows only Published or Draft transitions.
     * Validates: Requirement 4.3
     */
    @Property(tries = 100)
    fun reviewAllowsOnlyPublishedOrDraftTransition(
        @ForAll("articleStatuses") to: ArticleStatus
    ) {
        val result = validator.validate(ArticleStatus.Review, to)

        if (to == ArticleStatus.Published || to == ArticleStatus.Draft) {
            assert(result is TransitionResult.Valid) {
                "Review → ${to.value} should be Valid, but got $result"
            }
        } else {
            assert(result is TransitionResult.Invalid) {
                "Review → ${to.value} should be Invalid, but got $result"
            }
        }
    }

    /**
     * Property: Published is a terminal state with no valid transitions.
     * Validates: Requirement 4.4
     */
    @Property(tries = 100)
    fun publishedAllowsNoTransitions(
        @ForAll("articleStatuses") to: ArticleStatus
    ) {
        val result = validator.validate(ArticleStatus.Published, to)

        assert(result is TransitionResult.Invalid) {
            "Published → ${to.value} should be Invalid, but got $result"
        }
        val invalid = result as TransitionResult.Invalid
        assert(invalid.currentState == ArticleStatus.Published) {
            "currentState should be Published"
        }
        assert(invalid.allowed.isEmpty()) {
            "Published state should have empty allowed set, but got ${invalid.allowed.map { it.value }}"
        }
    }

    /**
     * Property: The allowedTransitions method returns the correct set for any status.
     */
    @Property(tries = 100)
    fun allowedTransitionsReturnsCorrectSetForAnyStatus(
        @ForAll("articleStatuses") from: ArticleStatus
    ) {
        val allowed = validator.allowedTransitions(from)

        val expected = when (from) {
            ArticleStatus.Draft -> setOf(ArticleStatus.Review)
            ArticleStatus.Review -> setOf(ArticleStatus.Published, ArticleStatus.Draft)
            ArticleStatus.Published -> emptySet()
        }

        assert(allowed == expected) {
            "Expected allowed transitions for ${from.value} to be ${expected.map { it.value }}, " +
                "but got ${allowed.map { it.value }}"
        }
    }
}
