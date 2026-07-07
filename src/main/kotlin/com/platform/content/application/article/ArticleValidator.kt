package com.platform.content.application.article

import com.platform.content.domain.FieldError
import com.platform.content.domain.ValidationException
import org.springframework.stereotype.Component

/**
 * Validates article fields at the domain/application level.
 * Collects all field-level violations and throws a single [ValidationException]
 * if any constraints are violated.
 *
 * FK reference checks (authorId, categoryId existence) are handled at the service layer.
 */
@Component
class ArticleValidator {

    companion object {
        const val TITLE_MAX_LENGTH = 255
        const val SUMMARY_MAX_LENGTH = 500
        const val TAGS_MAX_SIZE = 10
    }

    /**
     * Validates article input fields for creation or update.
     *
     * @param title the article title
     * @param body the article body
     * @param summary optional summary text
     * @param tags list of tags
     * @throws ValidationException if any field violates its constraints
     */
    fun validate(title: String, body: String, summary: String?, tags: List<String>) {
        val errors = mutableListOf<FieldError>()

        validateTitle(title, errors)
        validateBody(body, errors)
        validateSummary(summary, errors)
        validateTags(tags, errors)

        if (errors.isNotEmpty()) {
            throw ValidationException(errors)
        }
    }

    private fun validateTitle(title: String, errors: MutableList<FieldError>) {
        if (title.isBlank()) {
            errors.add(FieldError("title", "Title must not be blank"))
        } else if (title.length > TITLE_MAX_LENGTH) {
            errors.add(FieldError("title", "Title must not exceed $TITLE_MAX_LENGTH characters"))
        }
    }

    private fun validateBody(body: String, errors: MutableList<FieldError>) {
        if (body.isBlank()) {
            errors.add(FieldError("body", "Body must not be blank"))
        }
    }

    private fun validateSummary(summary: String?, errors: MutableList<FieldError>) {
        if (summary != null && summary.length > SUMMARY_MAX_LENGTH) {
            errors.add(FieldError("summary", "Summary must not exceed $SUMMARY_MAX_LENGTH characters"))
        }
    }

    private fun validateTags(tags: List<String>, errors: MutableList<FieldError>) {
        if (tags.size > TAGS_MAX_SIZE) {
            errors.add(FieldError("tags", "Tags must not exceed $TAGS_MAX_SIZE entries"))
        }
    }
}
