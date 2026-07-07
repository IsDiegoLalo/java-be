package com.platform.content.application.category

import com.platform.content.domain.FieldError
import com.platform.content.domain.ValidationException
import org.springframework.stereotype.Component

/**
 * Validates Category fields according to domain rules.
 *
 * Validation rules:
 * - name: must not be blank, between 2 and 100 characters
 * - description: optional, max 500 characters
 *
 * Note: Case-insensitive uniqueness check is handled at the service layer,
 * as it requires repository access.
 */
@Component
class CategoryValidator {

    companion object {
        const val NAME_MIN_LENGTH = 2
        const val NAME_MAX_LENGTH = 100
        const val DESCRIPTION_MAX_LENGTH = 500
    }

    /**
     * Validates the given category fields and throws [ValidationException] if any rule is violated.
     *
     * @param name the category name
     * @param description the optional category description
     * @throws ValidationException if one or more validation rules are violated
     */
    fun validate(name: String, description: String? = null) {
        val errors = mutableListOf<FieldError>()

        validateName(name, errors)
        validateDescription(description, errors)

        if (errors.isNotEmpty()) {
            throw ValidationException(errors)
        }
    }

    private fun validateName(name: String, errors: MutableList<FieldError>) {
        if (name.isBlank()) {
            errors.add(FieldError("name", "Category name must not be blank"))
            return
        }
        if (name.length < NAME_MIN_LENGTH) {
            errors.add(FieldError("name", "Category name must be at least $NAME_MIN_LENGTH characters"))
        }
        if (name.length > NAME_MAX_LENGTH) {
            errors.add(FieldError("name", "Category name must not exceed $NAME_MAX_LENGTH characters"))
        }
    }

    private fun validateDescription(description: String?, errors: MutableList<FieldError>) {
        if (description != null && description.length > DESCRIPTION_MAX_LENGTH) {
            errors.add(FieldError("description", "Category description must not exceed $DESCRIPTION_MAX_LENGTH characters"))
        }
    }
}
